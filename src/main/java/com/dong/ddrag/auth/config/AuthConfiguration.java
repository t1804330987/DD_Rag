package com.dong.ddrag.auth.config;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.auth.service.PasswordHasher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    private static final int BCRYPT_MAX_INPUT_BYTES = 72;

    @Bean
    Clock authClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    PasswordHasher passwordHasher() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return new PasswordHasher() {
            @Override
            public String hash(String rawPassword) {
                validateInputLength(rawPassword);
                return encoder.encode(rawPassword);
            }

            @Override
            public boolean matches(String rawPassword, String passwordHash) {
                validateInputLength(rawPassword);
                return encoder.matches(rawPassword, passwordHash);
            }

            private void validateInputLength(String rawValue) {
                if (rawValue == null || rawValue.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_INPUT_BYTES) {
                    throw new BusinessException("密码长度超过安全上限，请控制在 72 字节以内");
                }
            }
        };
    }
}
