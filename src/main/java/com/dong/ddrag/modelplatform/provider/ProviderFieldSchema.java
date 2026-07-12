package com.dong.ddrag.modelplatform.provider;

public record ProviderFieldSchema(
        String name, String type, boolean required, boolean sensitive, String defaultValue) {
}
