package com.dong.ddrag.modelplatform.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.genai.errors.ClientException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;

class ProviderExceptionClassifierProviderAdapterTest {
    private static final String SECRET_ERROR = "provider-error-with-secret-key";

    @Test
    void shouldClassifyProviderStatusWithoutExposingRawMessage() {
        ProviderAdapterException rateLimited = ProviderExceptionClassifier.classify(
                new HttpClientErrorException(HttpStatusCode.valueOf(429), SECRET_ERROR,
                        HttpHeaders.EMPTY, new byte[0], null));
        ProviderAdapterException authenticationFailed = ProviderExceptionClassifier.classify(
                new ClientException(401, "UNAUTHENTICATED", SECRET_ERROR));

        assertEquals(ProviderErrorCode.RATE_LIMITED, rateLimited.code());
        assertEquals(ProviderErrorCode.AUTHENTICATION_FAILED, authenticationFailed.code());
        assertFalse(rateLimited.toString().contains(SECRET_ERROR));
        assertFalse(authenticationFailed.toString().contains(SECRET_ERROR));
    }

    @Test
    void shouldClassifyNestedTimeout() {
        ProviderAdapterException classified = ProviderExceptionClassifier.classify(
                new IllegalStateException(SECRET_ERROR, new SocketTimeoutException(SECRET_ERROR)));

        assertEquals(ProviderErrorCode.HARD_TIMEOUT, classified.code());
        assertFalse(classified.toString().contains(SECRET_ERROR));
    }
}
