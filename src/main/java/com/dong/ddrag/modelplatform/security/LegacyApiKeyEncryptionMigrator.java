package com.dong.ddrag.modelplatform.security;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot style upgrade: rewrites legacy plaintext API keys to AES ciphertext on startup.
 * Safe to re-run; already-encrypted values are left untouched.
 */
@Component
@Order(50)
public class LegacyApiKeyEncryptionMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyApiKeyEncryptionMigrator.class);

    private final JdbcTemplate jdbcTemplate;
    private final ApiKeyCipher cipher;

    public LegacyApiKeyEncryptionMigrator(JdbcTemplate jdbcTemplate, ApiKeyCipher cipher) {
        this.jdbcTemplate = jdbcTemplate;
        this.cipher = cipher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                select id, api_key_plaintext
                from model_connections
                where status <> 'DELETED'
                  and api_key_plaintext is not null
                  and api_key_plaintext not like 'ENC$v1$%'
                """);
        if (rows.isEmpty()) {
            return;
        }
        int upgraded = 0;
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String plain = (String) row.get("api_key_plaintext");
            String encrypted = cipher.encrypt(plain);
            upgraded += jdbcTemplate.update(
                    """
                    update model_connections
                    set api_key_plaintext = ?, credential_storage_type = 'ENCRYPTED', updated_at = current_timestamp
                    where id = ? and api_key_plaintext = ?
                    """,
                    encrypted, id, plain);
        }
        log.info("Upgraded {} model connection API key(s) from plaintext to encrypted storage", upgraded);
    }
}
