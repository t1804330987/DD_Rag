package com.dong.ddrag.groupmembership.controller;

import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.groupmembership.service.GroupManagementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
public class InvitationDecisionController {

    private final GroupManagementService groupManagementService;

    public InvitationDecisionController(GroupManagementService groupManagementService) {
        this.groupManagementService = groupManagementService;
    }

    @PostMapping("/{invitationId}/accept")
    public ApiResponse<Void> acceptInvitation(
            @PathVariable Long invitationId,
            HttpServletRequest request
    ) {
        groupManagementService.acceptInvitation(request, invitationId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{invitationId}/reject")
    public ApiResponse<Void> rejectInvitation(
            @PathVariable Long invitationId,
            HttpServletRequest request
    ) {
        groupManagementService.rejectInvitation(request, invitationId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{invitationId}/cancel")
    public ApiResponse<Void> cancelInvitation(
            @PathVariable Long invitationId,
            HttpServletRequest request
    ) {
        groupManagementService.cancelInvitation(request, invitationId);
        return ApiResponse.success(null);
    }
}
