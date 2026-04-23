package com.dong.ddrag.auth.service;

import java.time.LocalDateTime;

public record RefreshTokenRecord(
        Long id,
        Long userId,
        String tokenId,
        String tokenHash,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt
) {

    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
