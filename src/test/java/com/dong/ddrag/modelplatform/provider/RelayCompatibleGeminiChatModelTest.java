package com.dong.ddrag.modelplatform.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dong.ddrag.modelplatform.model.enums.ProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

class RelayCompatibleGeminiChatModelTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void acceptsRelayResponseWithoutModelVersion() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        String body = """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"OK"}]},"finishReason":"STOP"}],\
                "usageMetadata":{"promptTokenCount":6,"candidatesTokenCount":1,"totalTokenCount":7}}
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().toString());
            apiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ProviderChatModelSettings settings = new ProviderChatModelSettings(
                ProviderType.GEMINI, baseUrl, "test-key", "gemini-3.1-flash-lite",
                Map.of("apiVersion", "v1beta"), Duration.ofSeconds(5));
        RelayCompatibleGeminiChatModel model = new RelayCompatibleGeminiChatModel(settings, new ObjectMapper());

        ChatResponse response = model.call(new Prompt("Reply with exactly: OK"));

        assertEquals("OK", response.getResult().getOutput().getText());
        assertEquals("gemini-3.1-flash-lite", response.getMetadata().getModel());
        assertEquals(6, response.getMetadata().getUsage().getPromptTokens());
        assertEquals(1, response.getMetadata().getUsage().getCompletionTokens());
        assertTrue(path.get().contains("/v1beta/models/gemini-3.1-flash-lite:generateContent"));
        assertFalse(path.get().contains("models/models/"));
        assertEquals("test-key", apiKey.get());
    }

    @Test
    void stripsApiVersionSuffixFromBaseUrl() {
        assertEquals("https://cccapi.top",
                RelayCompatibleGeminiChatModel.stripApiVersionSuffix("https://cccapi.top/v1beta"));
        assertEquals("https://cccapi.top",
                RelayCompatibleGeminiChatModel.stripApiVersionSuffix("https://cccapi.top/v1"));
        assertEquals("https://cccapi.top",
                RelayCompatibleGeminiChatModel.stripApiVersionSuffix("https://cccapi.top/"));
    }

    @Test
    void mapsHttpErrorsWithoutLeakingBody() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = "{\"error\":{\"message\":\"secret-key-value\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ProviderChatModelSettings settings = new ProviderChatModelSettings(
                ProviderType.GEMINI, baseUrl, "test-key", "gemini-3.1-flash-lite",
                Map.of(), Duration.ofSeconds(5));
        RelayCompatibleGeminiChatModel model = new RelayCompatibleGeminiChatModel(settings, new ObjectMapper());

        ProviderAdapterException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ProviderAdapterException.class, () -> model.call(new Prompt("hi")));
        assertEquals(ProviderErrorCode.AUTHENTICATION_FAILED, exception.code());
        assertFalse(exception.toString().contains("secret-key-value"));
    }
}
