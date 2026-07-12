package com.dong.ddrag.modelplatform.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ddrag.security")
public class ApiKeyEncryptionProperties {

    /**
     * Master secret used to derive the AES-256 key for model connection API keys.
     * Must be stable across restarts; rotating it without re-encrypting rows will break decryption.
     */
    @NotBlank
    private String apiKeyEncryptionSecret;

    public String getApiKeyEncryptionSecret() {
        return apiKeyEncryptionSecret;
    }

    public void setApiKeyEncryptionSecret(String apiKeyEncryptionSecret) {
        this.apiKeyEncryptionSecret = apiKeyEncryptionSecret;
    }
}
