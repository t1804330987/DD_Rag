package com.dong.ddrag.modelplatform.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Gemini chat over the public generateContent HTTP API.
 * <p>
 * Official Spring AI {@code GoogleGenAiChatModel} requires {@code modelVersion} in the response.
 * Many OpenAI-compatible Gemini relays return valid {@code candidates} without that field and then
 * fail model tests even when the upstream call succeeded. This client keeps the same request shape
 * ({@code /{apiVersion}/models/{model}:generateContent}) but tolerates missing metadata.
 */
final class RelayCompatibleGeminiChatModel implements ChatModel, AutoCloseable {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiVersion;
    private final String apiKey;
    private final String modelName;
    private final Duration timeout;
    private final ChatOptions defaultOptions;

    RelayCompatibleGeminiChatModel(ProviderChatModelSettings settings, ObjectMapper objectMapper) {
        Objects.requireNonNull(settings, "settings");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.baseUrl = stripTrailingSlash(settings.baseUrl());
        this.apiVersion = resolveApiVersion(settings);
        this.apiKey = settings.apiKey();
        this.modelName = settings.modelName();
        this.timeout = settings.transportDeadline() == null ? Duration.ofMinutes(6) : settings.transportDeadline();
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.defaultOptions = ChatOptions.builder().model(modelName).build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            JsonNode response = postJson(generateContentUri(), buildRequestBody(prompt, false));
            return toChatResponse(response);
        }
        catch (ProviderAdapterException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new ProviderAdapterException(ProviderErrorCode.NETWORK_ERROR, exception);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderAdapterException(ProviderErrorCode.NETWORK_ERROR, exception);
        }
        catch (RuntimeException exception) {
            throw ProviderExceptionClassifier.classify(exception);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> {
            try {
                return Flux.fromIterable(streamResponses(prompt));
            }
            catch (ProviderAdapterException exception) {
                return Flux.error(exception);
            }
            catch (IOException exception) {
                return Flux.error(new ProviderAdapterException(ProviderErrorCode.NETWORK_ERROR, exception));
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Flux.error(new ProviderAdapterException(ProviderErrorCode.NETWORK_ERROR, exception));
            }
            catch (RuntimeException exception) {
                return Flux.error(ProviderExceptionClassifier.classify(exception));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return defaultOptions;
    }

    @Override
    public void close() {
        // HttpClient has no close in Java 21; nothing to release.
    }

    private List<ChatResponse> streamResponses(Prompt prompt) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(streamGenerateContentUri())
                .timeout(timeout)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(prompt, true).toString(),
                        StandardCharsets.UTF_8))
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            requireSuccess(response.statusCode(), readLimited(response.body()));
        }
        List<ChatResponse> chunks = new ArrayList<>();
        try (InputStream body = response.body();
                BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            StringBuilder event = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    ChatResponse chunk = parseSseEvent(event.toString());
                    if (chunk != null) {
                        chunks.add(chunk);
                    }
                    event.setLength(0);
                    continue;
                }
                if (line.startsWith("data:")) {
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    if (!event.isEmpty()) {
                        event.append('\n');
                    }
                    event.append(payload);
                }
            }
            ChatResponse trailing = parseSseEvent(event.toString());
            if (trailing != null) {
                chunks.add(trailing);
            }
        }
        if (chunks.isEmpty()) {
            // Some relays ignore SSE and return a single JSON body; fall back to blocking call shape.
            chunks.add(call(prompt));
        }
        return chunks;
    }

    private ChatResponse parseSseEvent(String payload) throws IOException {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        JsonNode node = objectMapper.readTree(payload);
        if (node.has("error")) {
            throw invalidResponseFromBody(node);
        }
        if (!node.path("candidates").isArray() && !node.path("usageMetadata").isObject()) {
            return null;
        }
        return toChatResponse(node);
    }

    private JsonNode postJson(URI uri, ObjectNode body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        requireSuccess(response.statusCode(), response.body());
        return objectMapper.readTree(response.body() == null ? "{}" : response.body());
    }

    private ObjectNode buildRequestBody(Prompt prompt, boolean stream) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        String systemText = null;
        List<Message> messages = prompt == null ? List.of() : prompt.getInstructions();
        for (Message message : messages) {
            if (message instanceof SystemMessage systemMessage) {
                String text = systemMessage.getText();
                if (StringUtils.hasText(text)) {
                    systemText = systemText == null ? text : systemText + "\n" + text;
                }
                continue;
            }
            String role = toGeminiRole(message);
            String text = message.getText();
            if (!StringUtils.hasText(role) || !StringUtils.hasText(text)) {
                continue;
            }
            ObjectNode content = contents.addObject();
            content.put("role", role);
            content.putArray("parts").addObject().put("text", text);
        }
        if (contents.isEmpty()) {
            ObjectNode content = contents.addObject();
            content.put("role", "user");
            content.putArray("parts").addObject().put("text", "OK");
        }
        if (StringUtils.hasText(systemText)) {
            ObjectNode systemInstruction = root.putObject("systemInstruction");
            systemInstruction.putArray("parts").addObject().put("text", systemText);
        }
        if (stream) {
            // Keep body identical for relays that ignore the stream flag; path selects SSE.
        }
        return root;
    }

    private ChatResponse toChatResponse(JsonNode response) {
        if (response != null && response.has("error")) {
            throw invalidResponseFromBody(response);
        }
        String text = extractText(response);
        Generation generation = new Generation(new AssistantMessage(text == null ? "" : text));
        Integer promptTokens = intOrNull(response, "usageMetadata", "promptTokenCount");
        Integer completionTokens = intOrNull(response, "usageMetadata", "candidatesTokenCount");
        Integer totalTokens = intOrNull(response, "usageMetadata", "totalTokenCount");
        if (totalTokens == null && promptTokens != null && completionTokens != null) {
            totalTokens = promptTokens + completionTokens;
        }
        String model = textOr(response, "modelVersion", modelName);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model(model)
                .usage(new DefaultUsage(
                        promptTokens == null ? 0 : promptTokens,
                        completionTokens == null ? 0 : completionTokens,
                        totalTokens == null ? 0 : totalTokens))
                .build();
        return new ChatResponse(List.of(generation), metadata);
    }

    private static String extractText(JsonNode response) {
        if (response == null) {
            return "";
        }
        JsonNode candidates = response.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                String value = part.path("text").asText(null);
                if (StringUtils.hasText(value)) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(value);
                }
            }
        }
        return text.toString();
    }

    private URI generateContentUri() {
        return URI.create(baseUrl + "/" + apiVersion + "/models/" + encodeModel(modelName) + ":generateContent");
    }

    private URI streamGenerateContentUri() {
        return URI.create(baseUrl + "/" + apiVersion + "/models/" + encodeModel(modelName)
                + ":streamGenerateContent?alt=sse");
    }

    private static String encodeModel(String modelName) {
        // Gemini model ids are path segments; keep slash-free names as-is. Relays reject models/ prefix.
        String name = modelName.startsWith("models/") ? modelName.substring("models/".length()) : modelName;
        return name;
    }

    private static String toGeminiRole(Message message) {
        if (message == null) {
            return null;
        }
        MessageType type = message.getMessageType();
        if (type == MessageType.ASSISTANT) {
            return "model";
        }
        if (type == MessageType.USER || type == MessageType.TOOL) {
            return "user";
        }
        return null;
    }

    private void requireSuccess(int statusCode, String body) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        if (statusCode == 401 || statusCode == 403) {
            throw new ProviderAdapterException(ProviderErrorCode.AUTHENTICATION_FAILED);
        }
        if (statusCode == 429) {
            throw new ProviderAdapterException(ProviderErrorCode.RATE_LIMITED);
        }
        if (statusCode >= 500) {
            throw new ProviderAdapterException(ProviderErrorCode.PROVIDER_UNAVAILABLE);
        }
        throw new ProviderAdapterException(ProviderErrorCode.INVALID_RESPONSE);
    }

    private ProviderAdapterException invalidResponseFromBody(JsonNode response) {
        int status = response.path("error").path("code").asInt(0);
        if (status == 401 || status == 403) {
            return new ProviderAdapterException(ProviderErrorCode.AUTHENTICATION_FAILED);
        }
        if (status == 429) {
            return new ProviderAdapterException(ProviderErrorCode.RATE_LIMITED);
        }
        if (status >= 500) {
            return new ProviderAdapterException(ProviderErrorCode.PROVIDER_UNAVAILABLE);
        }
        return new ProviderAdapterException(ProviderErrorCode.INVALID_RESPONSE);
    }

    private static Integer intOrNull(JsonNode root, String objectField, String valueField) {
        if (root == null) {
            return null;
        }
        JsonNode value = root.path(objectField).path(valueField);
        return value.isNumber() ? value.intValue() : null;
    }

    private static String textOr(JsonNode root, String field, String fallback) {
        if (root == null) {
            return fallback;
        }
        String value = root.path(field).asText(null);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String readLimited(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try {
            byte[] bytes = stream.readNBytes(512);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        catch (IOException ignored) {
            return "";
        }
    }

    private static String resolveApiVersion(ProviderChatModelSettings settings) {
        Object configured = settings.options() == null ? null : settings.options().get("apiVersion");
        if (configured != null && StringUtils.hasText(configured.toString())) {
            return configured.toString().replaceAll("^/+|/+$", "");
        }
        String base = stripTrailingSlash(settings.baseUrl());
        if (base.endsWith("/v1beta")) {
            return "v1beta";
        }
        if (base.endsWith("/v1")) {
            return "v1";
        }
        return "v1beta";
    }

    static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    static String stripApiVersionSuffix(String baseUrl) {
        String normalized = stripTrailingSlash(baseUrl);
        if (normalized == null) {
            return null;
        }
        if (normalized.endsWith("/v1beta")) {
            return normalized.substring(0, normalized.length() - "/v1beta".length());
        }
        if (normalized.endsWith("/v1")) {
            return normalized.substring(0, normalized.length() - "/v1".length());
        }
        return normalized;
    }
}
