package com.dong.ddrag.modelplatform.provider;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

record ProviderHttpRequest(URI uri, Map<String, String> headers, Duration deadline) {
    ProviderHttpRequest {
        headers = Map.copyOf(headers);
    }

    @Override
    public String toString() {
        return "ProviderHttpRequest{host='" + uri.getHost() + "', deadline=" + deadline
                + ", headerNames=" + headers.keySet() + "}";
    }
}
