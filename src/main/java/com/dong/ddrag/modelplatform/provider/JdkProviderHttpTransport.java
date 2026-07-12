package com.dong.ddrag.modelplatform.provider;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class JdkProviderHttpTransport implements ProviderHttpTransport {
    private final HttpClient client;

    JdkProviderHttpTransport(Duration connectTimeout) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    @Override
    public ProviderHttpResponse get(ProviderHttpRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .GET()
                .timeout(request.deadline());
        request.headers().forEach(builder::header);
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new ProviderHttpResponse(response.statusCode(), response.body());
        }
        catch (java.net.http.HttpTimeoutException exception) {
            throw new ProviderAdapterException(ProviderErrorCode.HARD_TIMEOUT, exception);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderAdapterException(ProviderErrorCode.NETWORK_ERROR, exception);
        }
        catch (IOException exception) {
            throw new ProviderAdapterException(ProviderErrorCode.NETWORK_ERROR, exception);
        }
    }
}
