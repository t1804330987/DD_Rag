package com.dong.ddrag.modelplatform.security;

/**
 * Process-wide holder so MyBatis XML TypeHandlers (no-arg constructed) can access the cipher bean.
 */
public final class ApiKeyCipherHolder {

    private static volatile ApiKeyCipher cipher;

    private ApiKeyCipherHolder() {
    }

    public static void set(ApiKeyCipher value) {
        cipher = value;
    }

    public static ApiKeyCipher require() {
        ApiKeyCipher current = cipher;
        if (current == null) {
            throw new IllegalStateException("ApiKeyCipher is not initialized");
        }
        return current;
    }

    /** Test-only reset helper. */
    static void clear() {
        cipher = null;
    }
}
