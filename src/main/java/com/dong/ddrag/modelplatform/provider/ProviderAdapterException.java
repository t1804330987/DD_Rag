package com.dong.ddrag.modelplatform.provider;

public final class ProviderAdapterException extends RuntimeException {
    private final ProviderErrorCode code;

    public ProviderAdapterException(ProviderErrorCode code) {
        super("Provider operation failed: " + code.name());
        this.code = code;
    }

    public ProviderAdapterException(ProviderErrorCode code, Throwable cause) {
        super("Provider operation failed: " + code.name());
        this.code = code;
    }

    public ProviderErrorCode code() {
        return code;
    }
}
