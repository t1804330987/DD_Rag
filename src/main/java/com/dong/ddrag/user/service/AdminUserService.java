package com.dong.ddrag.user.service;

import com.dong.ddrag.auth.security.RefreshTokenService;
import com.dong.ddrag.auth.service.PasswordHasher;
import com.dong.ddrag.common.enums.UserStatus;
import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.user.model.dto.UpdateUserStatusRequest;
import com.dong.ddrag.user.model.vo.AdminUserItemResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserService {

    private final JdbcTemplate jdbcTemplate;
    private final RefreshTokenService refreshTokenService;
    private final UserQueryService userQueryService;

    public AdminUserService(
            JdbcTemplate jdbcTemplate,
            RefreshTokenService refreshTokenService,
            UserQueryService userQueryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
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

    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("用户ID非法");
        }
        return userId;
    }
}
