package com.dong.ddrag.user.controller;

import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.identity.service.CurrentUserService;
import com.dong.ddrag.user.model.dto.ResetUserPasswordRequest;
import com.dong.ddrag.user.model.dto.UpdateUserStatusRequest;
import com.dong.ddrag.user.model.vo.AdminUserItemResponse;
import com.dong.ddrag.user.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final CurrentUserService currentUserService;
    private final AdminUserService adminUserService;

    public AdminUserController(
            CurrentUserService currentUserService,
            AdminUserService adminUserService
    ) {
        this.currentUserService = currentUserService;
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<List<AdminUserItemResponse>> listUsers(HttpServletRequest request) {
        currentUserService.requireSystemAdmin(request);
        return ApiResponse.success(adminUserService.listUsers());
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminUserItemResponse> getUser(
            @PathVariable Long userId,
            HttpServletRequest request
    ) {
        currentUserService.requireSystemAdmin(request);
        return ApiResponse.success(adminUserService.getUser(userId));
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<Void> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            HttpServletRequest httpServletRequest
    ) {
        currentUserService.requireSystemAdmin(httpServletRequest);
        adminUserService.updateUserStatus(userId, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/{userId}/reset-password")
    public ApiResponse<Void> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ResetUserPasswordRequest request,
            HttpServletRequest httpServletRequest
    ) {
        currentUserService.requireSystemAdmin(httpServletRequest);
        adminUserService.resetPassword(userId, request);
        return ApiResponse.success(null);
    }
}
