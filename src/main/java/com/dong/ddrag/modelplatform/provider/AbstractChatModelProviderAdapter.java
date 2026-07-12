package com.dong.ddrag.modelplatform.provider;

import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.StringUtils;

abstract class AbstractChatModelProviderAdapter implements ChatModelProviderAdapter {
    static final Duration DEFAULT_HARD_DEADLINE = Duration.ofMinutes(6);

    private final ProviderConnectionSchema schema;
    private final ProviderHttpTransport transport;
    private final ProviderChatModelFactory chatModelFactory;
    private final ObjectMapper objectMapper;
    private final Duration hardDeadline;

    AbstractChatModelProviderAdapter(
            ProviderConnectionSchema schema,
            ProviderHttpTransport transport,
            ProviderChatModelFactory chatModelFactory,
            ObjectMapper objectMapper,
            Duration hardDeadline) {
        this.schema = schema;
        this.transport = transport;
        this.chatModelFactory = chatModelFactory;
        this.objectMapper = objectMapper;
        this.hardDeadline = hardDeadline;
    }

    @Override
    public final ProviderType providerType() {
        return schema.providerType();
    }

    @Override
    public final ProviderConnectionSchema connectionSchema() {
        return schema;
    }

    @Override
    public void validateConnectionOptions(Map<String, Object> options) {
        Map<String, Object> values = options == null ? Map.of() : options;
        for (ProviderFieldSchema field : schema.fields()) {
            if (field.name().equals("baseUrl") || field.name().equals("apiKey")) {
                continue;
            }
            Object value = values.get(field.name());
            if (field.required() && !StringUtils.hasText(value == null ? null : value.toString())
                    && !StringUtils.hasText(field.defaultValue())) {
                throw new ProviderAdapterException(ProviderErrorCode.INVALID_CONFIGURATION);
            }
        }
        for (String name : values.keySet()) {
            if (schema.fields().stream().noneMatch(field -> field.name().equals(name)
                    && !field.name().equals("baseUrl") && !field.name().equals("apiKey"))) {
                throw new ProviderAdapterException(ProviderErrorCode.INVALID_CONFIGURATION);
            }
        }
    }

    @Override
    public final ChatModel createChatModel(ProviderConnectionSnapshot connection, String modelName) {
        validate(connection);
        if (!StringUtils.hasText(modelName)) {
            throw new ProviderAdapterException(ProviderErrorCode.INVALID_CONFIGURATION);
        }
        ProviderChatModelSettings settings = new ProviderChatModelSettings(
                providerType(), resolvedBaseUrl(connection), connection.apiKey(), modelName,
                connection.options(), hardDeadline);
        try {
            return new DeadlineEnforcingChatModel(chatModelFactory.create(settings), hardDeadline);
        }
        catch (RuntimeException exception) {
            throw ProviderExceptionClassifier.classify(exception);
        }
    }

    @Override
    public final List<DiscoveredModel> discoverModels(ProviderConnectionSnapshot connection) {
        validate(connection);
        ProviderHttpResponse response = transport.get(new ProviderHttpRequest(
                discoveryUri(resolvedBaseUrl(connection), connection), discoveryHeaders(connection), hardDeadline));
        requireSuccess(response.statusCode());
        try {
            return parseModels(objectMapper.readTree(response.body()));
        }
        catch (Exception exception) {
            throw new ProviderAdapterException(ProviderErrorCode.INVALID_RESPONSE, exception);
        }
    }

    @Override
    public final ConnectionProbeResult probeConnection(ProviderConnectionSnapshot connection) {
        List<DiscoveredModel> models = discoverModels(connection);
        return new ConnectionProbeResult(true, models.size());
    }

    protected final String resolvedBaseUrl(ProviderConnectionSnapshot connection) {
        return StringUtils.hasText(connection.baseUrl()) ? stripTrailingSlash(connection.baseUrl()) : schema.defaultBaseUrl();
    }

    protected List<DiscoveredModel> parseModels(JsonNode root) {
        JsonNode items = root.path("data");
        if (!items.isArray()) {
            items = root.path("models");
        }
        if (!items.isArray()) {
            throw new ProviderAdapterException(ProviderErrorCode.INVALID_RESPONSE);
        }
        List<DiscoveredModel> models = new ArrayList<>();
        for (JsonNode item : items) {
            String name = item.path("id").asText(item.path("name").asText());
            if (StringUtils.hasText(name)) {
                models.add(new DiscoveredModel(name.replaceFirst("^models/", ""),
                        item.path("displayName").asText(name)));
            }
        }
        return List.copyOf(models);
    }

    protected abstract URI discoveryUri(String baseUrl, ProviderConnectionSnapshot connection);

    protected abstract Map<String, String> discoveryHeaders(ProviderConnectionSnapshot connection);

    private void validate(ProviderConnectionSnapshot connection) {
        if (connection == null || !StringUtils.hasText(connection.apiKey())) {
            throw new ProviderAdapterException(ProviderErrorCode.INVALID_CONFIGURATION);
        }
        validateConnectionOptions(connection.options());
        try {
            URI uri = URI.create(resolvedBaseUrl(connection));
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new ProviderAdapterException(ProviderErrorCode.INVALID_CONFIGURATION);
            }
        }
        catch (IllegalArgumentException exception) {
            throw new ProviderAdapterException(ProviderErrorCode.INVALID_CONFIGURATION);
        }
    }

    private static void requireSuccess(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        if (statusCode == 401 || statusCode == 403) {
            throw new ProviderAdapterException(ProviderErrorCode.AUTHENTICATION_FAILED);
        }
        if (statusCode == 429) {
            throw new ProviderAdapterException(ProviderErrorCode.RATE_LIMITED);
        }
        throw new ProviderAdapterException(statusCode >= 500
                ? ProviderErrorCode.PROVIDER_UNAVAILABLE : ProviderErrorCode.INVALID_RESPONSE);
    }

    static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
