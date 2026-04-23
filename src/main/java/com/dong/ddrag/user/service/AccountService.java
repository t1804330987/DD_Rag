package com.dong.ddrag.user.service;

import com.dong.ddrag.auth.security.RefreshTokenService;
import com.dong.ddrag.auth.service.PasswordHasher;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.user.model.dto.ChangePasswordRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenService refreshTokenService;

    public AccountService(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            RefreshTokenService refreshTokenService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public void changePassword(CurrentUserService.CurrentUser currentUser, ChangePasswordRequest request) {
        validatePasswordPolicy(request.newPassword());
        UserCredential userCredential = loadCredential(currentUser.userId());
        if (userCredential == null || userCredential.passwordHash() == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordHasher.matches(request.currentPassword(), userCredential.passwordHash())) {
            throw new BusinessException("当前密码不正确");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        int updated = jdbcTemplate.update(
                """
                update users
                set password_hash = ?, must_change_password = false, updated_at = now()
                where id = ?
                """,
                passwordHasher.hash(request.newPassword()),
                currentUser.userId()
        );
        if (updated == 0) {
            throw new BusinessException("用户不存在");
        }
        refreshTokenService.revokeActiveTokens(currentUser.userId());
    }

    private void validatePasswordPolicy(String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("新密码必须至少 8 位，且同时包含字母和数字");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < newPassword.length(); i++) {
            char current = newPassword.charAt(i);
            if (Character.isLetter(current)) {
                hasLetter = true;
            }
            if (Character.isDigit(current)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new BusinessException("新密码必须至少 8 位，且同时包含字母和数字");
        }
    }

    private UserCredential loadCredential(Long userId) {
        return jdbcTemplate.query(
                """
                select id, password_hash
                from users
                where id = ?
                """,
                resultSet -> resultSet.next()
                        ? new UserCredential(resultSet.getLong("id"), resultSet.getString("password_hash"))
                        : null,
                userId
        );
    }

    private record UserCredential(Long userId, String passwordHash) {
    }
}
