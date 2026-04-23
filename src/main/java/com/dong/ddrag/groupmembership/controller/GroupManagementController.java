package com.dong.ddrag.groupmembership.controller;

import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.groupmembership.model.dto.CreateGroupRequest;
import com.dong.ddrag.groupmembership.model.dto.CreateInvitationRequest;
import com.dong.ddrag.groupmembership.model.vo.GroupMemberVO;
import com.dong.ddrag.groupmembership.service.GroupManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupManagementController {

    private final GroupManagementService groupManagementService;

    public GroupManagementController(GroupManagementService groupManagementService) {
        this.groupManagementService = groupManagementService;
    }

    @PostMapping
    public ApiResponse<Long> createGroup(
            @Valid @RequestBody CreateGroupRequest createGroupRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(groupManagementService.createGroup(request, createGroupRequest));
    }

    @PostMapping("/{groupId}/invitations")
    public ApiResponse<Long> createInvitation(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateInvitationRequest createInvitationRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(groupManagementService.createInvitation(request, groupId, createInvitationRequest));
    }

    @GetMapping("/{groupId}/members")
    public List<GroupMemberVO> listMembers(
            @PathVariable Long groupId,
            HttpServletRequest request
    ) {
        return groupManagementService.listMembers(request, groupId);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            HttpServletRequest request
    ) {
        groupManagementService.removeMember(request, groupId, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{groupId}/leave")
    public ApiResponse<Void> leaveGroup(
            @PathVariable Long groupId,
            HttpServletRequest request
    ) {
        groupManagementService.leaveGroup(request, groupId);
        return ApiResponse.success(null);
    }
}
