package com.dong.ddrag.user.service;

import com.dong.ddrag.auth.security.RefreshTokenService;
import com.dong.ddrag.auth.service.PasswordHasher;
import com.dong.ddrag.common.enums.UserStatus;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.user.model.dto.CreateUserRequest;
import com.dong.ddrag.user.model.dto.ResetUserPasswordRequest;
import com.dong.ddrag.user.model.dto.UpdateUserStatusRequest;
import com.dong.ddrag.user.model.vo.AdminUserItemResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenService refreshTokenService;
    private final UserQueryService userQueryService;

    public AdminUserService(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            RefreshTokenService refreshTokenService,
            UserQueryService userQueryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.refreshTokenService = refreshTokenService;
        this.userQueryService = userQueryService;
    }

    public List<AdminUserItemResponse> listUsers() {
        return userQueryService.listUsers();
    }

    public AdminUserItemResponse getUser(Long userId) {
        return userQueryService.getUser(requireUserId(userId));
    }

    @Transactional
    public AdminUserItemResponse createUser(CreateUserRequest request) {
        String username = normalizeRequiredValue(request.username(), "用户名不能为空", "用户名长度不能超过 64", 64);
        String email = normalizeRequiredValue(request.email(), "邮箱不能为空", "邮箱长度不能超过 128", 128);
        String displayName = normalizeRequiredValue(request.displayName(), "显示名称不能为空", "显示名称长度不能超过 128", 128);
        if (userQueryService.existsByUsername(username)) {
            throw new BusinessException("用户名已存在");
        }
        if (userQueryService.existsByEmail(email)) {
            throw new BusinessException("邮箱已存在");
        }
        validatePasswordPolicy(request.initialPassword());
        Long userId = jdbcTemplate.queryForObject(
                """
                insert into users (
                    user_code, username, email, display_name, password_hash,
                    system_role, status, must_change_password, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                returning id
                """,
                Long.class,
                username,
                username,
                email,
                displayName,
                passwordHasher.hash(request.initialPassword()),
                request.systemRole().name(),
                UserStatus.ACTIVE.name(),
                request.mustChangePassword()
        );
        return userQueryService.getUser(userId);
    }

    @Transactional
    public void updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        int updated = jdbcTemplate.update(
                "update users set status = ?, updated_at = now() where id = ?",
                request.status().name(),
                requireUserId(userId)
        );
        if (updated == 0) {
            throw new BusinessException("用户不存在");
        }
        if (request.status() == UserStatus.DISABLED) {
            refreshTokenService.revokeActiveTokens(userId);
        }
    }

    @Transactional
    public void resetPassword(Long userId, ResetUserPasswordRequest request) {
        validatePasswordPolicy(request.newPassword());
        int updated = jdbcTemplate.update(
                """
                update users
                set password_hash = ?, must_change_password = true, updated_at = now()
                where id = ?
                """,
                passwordHasher.hash(request.newPassword()),
                requireUserId(userId)
        );
        if (updated == 0) {
            throw new BusinessException("用户不存在");
        }
        refreshTokenService.revokeActiveTokens(userId);
    }

    private void validatePasswordPolicy(String password) {
        int minPasswordLength = 8;
        int bcryptMaxPasswordBytes = 72;
        if (password == null || password.length() < minPasswordLength) {
            throw new BusinessException("新密码必须至少 8 位，且同时包含字母和数字");
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > bcryptMaxPasswordBytes) {
            throw new BusinessException("密码长度超过安全上限，请控制在 72 字节以内");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char current = password.charAt(i);
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

    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("用户ID非法");
        }
        return userId;
    }

    private String normalizeRequiredValue(String value, String blankMessage, String lengthMessage, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(blankMessage);
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maxLength) {
            throw new BusinessException(lengthMessage);
        }
        return normalizedValue;
    }
}
