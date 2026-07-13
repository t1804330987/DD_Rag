package com.dong.ddrag.modelplatform.provider;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

final class SpringAiProviderModels {
    private static final RetryTemplate NO_RETRY = RetryTemplate.builder().maxAttempts(1).noBackoff().build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SpringAiProviderModels() {
    }

    static ChatModel openAi(ProviderChatModelSettings settings) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(settings.baseUrl())
                .apiKey(settings.apiKey())
                .restClientBuilder(restClient(settings.transportDeadline()))
                .webClientBuilder(webClient(settings.transportDeadline()))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(settings.modelName()).streamUsage(true).build())
                .retryTemplate(NO_RETRY)
                .build();
    }

    static ChatModel anthropic(ProviderChatModelSettings settings) {
        String version = option(settings, "anthropicVersion", "2023-06-01");
        // Spring AI always appends /v1/messages. If the connection baseUrl already ends with /v1,
        // strip it so relays configured as either host root or host/v1 both resolve correctly.
        String baseUrl = stripTrailingVersion(settings.baseUrl(), "/v1");
        AnthropicApi api = AnthropicApi.builder()
                .baseUrl(baseUrl)
                .apiKey(settings.apiKey())
                .anthropicVersion(version)
                .completionsPath("/v1/messages")
                .restClientBuilder(restClient(settings.transportDeadline()))
                .webClientBuilder(webClient(settings.transportDeadline()))
                .build();
        return AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(AnthropicChatOptions.builder().model(settings.modelName()).maxTokens(1024).build())
                .retryTemplate(NO_RETRY)
                .build();
    }

    static ChatModel gemini(ProviderChatModelSettings settings) {
        // Use relay-compatible HTTP client: many Gemini proxies omit modelVersion and break
        // Spring AI's GoogleGenAiChatModel response parsing even when candidates are valid.
        ProviderChatModelSettings normalized = new ProviderChatModelSettings(
                settings.providerType(),
                RelayCompatibleGeminiChatModel.stripApiVersionSuffix(settings.baseUrl()),
                settings.apiKey(),
                settings.modelName(),
                settings.options(),
                settings.transportDeadline());
        return new RelayCompatibleGeminiChatModel(normalized, OBJECT_MAPPER);
    }

    static ChatModel dashScope(ProviderChatModelSettings settings) {
        DashScopeApi.Builder apiBuilder = DashScopeApi.builder()
                .baseUrl(settings.baseUrl())
                .apiKey(settings.apiKey())
                .restClientBuilder(restClient(settings.transportDeadline()))
                .webClientBuilder(webClient(settings.transportDeadline()));
        String workspaceId = option(settings, "workspaceId", null);
        if (workspaceId != null && !workspaceId.isBlank()) {
            apiBuilder.workSpaceId(workspaceId);
        }
        DashScopeApi api = apiBuilder.build();
        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(DashScopeChatOptions.builder().model(settings.modelName()).build())
                .retryTemplate(NO_RETRY)
                .build();
    }

    private static RestClient.Builder restClient(Duration deadline) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(deadline);
        return RestClient.builder().requestFactory(requestFactory);
    }

    private static WebClient.Builder webClient(Duration deadline) {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(deadline)
                .build();
        return WebClient.builder().clientConnector(new JdkClientHttpConnector(client));
    }

    private static String option(ProviderChatModelSettings settings, String name, String defaultValue) {
        Object value = settings.options().get(name);
        return value == null ? defaultValue : value.toString();
    }

    private static String stripTrailingVersion(String baseUrl, String versionSuffix) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith(versionSuffix)) {
            return normalized.substring(0, normalized.length() - versionSuffix.length());
        }
        return normalized;
    }
}
