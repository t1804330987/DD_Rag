package com.dong.ddrag.auth.security;

import com.dong.ddrag.auth.config.AuthProperties;
import com.dong.ddrag.auth.service.PasswordHasher;
import com.dong.ddrag.auth.service.RefreshTokenRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final String TOKEN_SEPARATOR = ".";
    private static final int SECRET_BYTES = 24;
    private static final RowMapper<RefreshTokenRecord> TOKEN_ROW_MAPPER = (resultSet, rowNum) -> new RefreshTokenRecord(
            resultSet.getLong("id"),
            resultSet.getLong("user_id"),
            resultSet.getString("token_id"),
            resultSet.getString("token_hash"),
            resultSet.getTimestamp("expires_at").toLocalDateTime(),
            Optional.ofNullable(resultSet.getTimestamp("revoked_at"))
                    .map(timestamp -> timestamp.toLocalDateTime())
                    .orElse(null)
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final AuthProperties authProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            AuthProperties authProperties,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    public IssuedRefreshToken issueToken(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = tokenId + TOKEN_SEPARATOR + newTokenSecret();
        LocalDateTime expiresAt = now.plusDays(authProperties.getRefreshTokenExpireDays());
        Long id = jdbcTemplate.queryForObject(
                """
                insert into user_refresh_tokens (user_id, token_id, token_hash, expires_at, created_at)
                values (?, ?, ?, ?, ?)
                returning id
                """,
                Long.class,
                userId,
                tokenId,
                passwordHasher.hash(refreshToken),
                expiresAt,
                now
        );
        return new IssuedRefreshToken(refreshToken, findById(id).orElseThrow());
    }

    public Optional<RefreshTokenRecord> findActiveToken(String refreshToken) {
        Optional<ParsedRefreshToken> parsedToken = parseToken(refreshToken);
        if (parsedToken.isEmpty()) {
            return Optional.empty();
        }
        Optional<RefreshTokenRecord> storedToken = findByTokenId(parsedToken.get().tokenId());
        if (storedToken.isEmpty()) {
            return Optional.empty();
        }
        RefreshTokenRecord record = storedToken.get();
        if (!passwordHasher.matches(refreshToken, record.tokenHash())) {
            return Optional.empty();
        }
        if (!record.isActive(LocalDateTime.now(clock))) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    public void revokeActiveTokens(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        jdbcTemplate.update(
                """
                update user_refresh_tokens
                set revoked_at = ?
                where user_id = ?
                  and revoked_at is null
                  and expires_at > ?
                """,
                now,
                userId,
                now
        );
    }

    public void revokeToken(String refreshToken) {
        Optional<RefreshTokenRecord> activeToken = findActiveToken(refreshToken);
        if (activeToken.isEmpty()) {
            return;
        }
        jdbcTemplate.update(
                "update user_refresh_tokens set revoked_at = ? where id = ?",
                LocalDateTime.now(clock),
                activeToken.get().id()
        );
    }

    public long countActiveTokens(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from user_refresh_tokens
                where user_id = ?
                  and revoked_at is null
                  and expires_at > ?
                """,
                Long.class,
                userId,
                LocalDateTime.now(clock)
        );
        return count == null ? 0 : count;
    }

    private Optional<RefreshTokenRecord> findById(Long id) {
        List<RefreshTokenRecord> tokens = jdbcTemplate.query(
                """
                select id, user_id, token_id, token_hash, expires_at, revoked_at
                from user_refresh_tokens
                where id = ?
                """,
                TOKEN_ROW_MAPPER,
                id
        );
        return tokens.stream().findFirst();
    }

    private Optional<RefreshTokenRecord> findByTokenId(String tokenId) {
        List<RefreshTokenRecord> tokens = jdbcTemplate.query(
                """
                select id, user_id, token_id, token_hash, expires_at, revoked_at
                from user_refresh_tokens
                where token_id = ?
                """,
                TOKEN_ROW_MAPPER,
                tokenId
        );
        return tokens.stream().findFirst();
    }

    private Optional<ParsedRefreshToken> parseToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        String[] segments = refreshToken.trim().split("\\Q" + TOKEN_SEPARATOR + "\\E", 2);
        if (segments.length != 2 || segments[0].isBlank() || segments[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedRefreshToken(segments[0], segments[1]));
    }

    private String newTokenSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record ParsedRefreshToken(String tokenId, String secret) {
    }

    public record IssuedRefreshToken(String refreshToken, RefreshTokenRecord record) {
    }
}
