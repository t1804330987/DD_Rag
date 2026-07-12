package com.dong.ddrag.modelplatform.provider;

record ProviderHttpResponse(int statusCode, String body) {
    @Override
    public String toString() {
        return "ProviderHttpResponse{statusCode=" + statusCode + "}";
    }
}
