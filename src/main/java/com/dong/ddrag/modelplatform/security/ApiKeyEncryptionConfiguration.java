package com.dong.ddrag.modelplatform.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ApiKeyEncryptionProperties.class)
public class ApiKeyEncryptionConfiguration {

    @Bean
    public ApiKeyCipher apiKeyCipher(ApiKeyEncryptionProperties properties) {
        ApiKeyCipher cipher = new ApiKeyCipher(properties.getApiKeyEncryptionSecret());
        ApiKeyCipherHolder.set(cipher);
        return cipher;
    }
}
