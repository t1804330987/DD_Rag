package com.dong.ddrag.modelplatform.provider;

import com.google.genai.errors.ApiException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

final class ProviderExceptionClassifier {
    private ProviderExceptionClassifier() {
    }

    static ProviderAdapterException classify(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ProviderAdapterException providerError) {
                return providerError;
            }
            if (current instanceof SocketTimeoutException || current instanceof TimeoutException) {
                return new ProviderAdapterException(ProviderErrorCode.HARD_TIMEOUT, error);
            }
            if (current instanceof ConnectException) {
                return new ProviderAdapterException(ProviderErrorCode.NETWORK_ERROR, error);
            }
            if (current instanceof RestClientResponseException responseError) {
                return fromStatus(responseError.getStatusCode().value(), error);
            }
            if (current instanceof WebClientResponseException responseError) {
                return fromStatus(responseError.getStatusCode().value(), error);
            }
            if (current instanceof ApiException apiError) {
                return fromStatus(apiError.code(), error);
            }
        }
        return new ProviderAdapterException(ProviderErrorCode.PROVIDER_UNAVAILABLE, error);
    }

    private static ProviderAdapterException fromStatus(int status, Throwable error) {
        ProviderErrorCode code = switch (status) {
            case 401, 403 -> ProviderErrorCode.AUTHENTICATION_FAILED;
            case 429 -> ProviderErrorCode.RATE_LIMITED;
            default -> status >= 500 ? ProviderErrorCode.PROVIDER_UNAVAILABLE : ProviderErrorCode.INVALID_RESPONSE;
        };
        return new ProviderAdapterException(code, error);
    }
}
