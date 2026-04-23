package com.dong.ddrag.groupmembership.controller;

import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.groupmembership.model.dto.CreateJoinRequestRequest;
import com.dong.ddrag.groupmembership.model.vo.MyJoinRequestVO;
import com.dong.ddrag.groupmembership.model.vo.OwnerJoinRequestVO;
import com.dong.ddrag.groupmembership.service.GroupJoinRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupJoinRequestController {

    private final GroupJoinRequestService groupJoinRequestService;

    public GroupJoinRequestController(GroupJoinRequestService groupJoinRequestService) {
        this.groupJoinRequestService = groupJoinRequestService;
    }

    @PostMapping("/join-requests")
    public ApiResponse<Long> submitJoinRequest(
            @Valid @RequestBody CreateJoinRequestRequest joinRequestRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(groupJoinRequestService.submitJoinRequest(request, joinRequestRequest));
    }

    @GetMapping("/join-requests/my")
    public ApiResponse<List<MyJoinRequestVO>> listMyJoinRequests(HttpServletRequest request) {
        return ApiResponse.success(groupJoinRequestService.listMyJoinRequests(request));
    }

    @GetMapping("/{groupId}/join-requests")
    public ApiResponse<List<OwnerJoinRequestVO>> listOwnerJoinRequests(
            @PathVariable Long groupId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(groupJoinRequestService.listOwnerJoinRequests(request, groupId));
    }

    @PostMapping("/{groupId}/join-requests/{requestId}/approve")
    public ApiResponse<Void> approveJoinRequest(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            HttpServletRequest request
    ) {
        groupJoinRequestService.approveJoinRequest(request, groupId, requestId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{groupId}/join-requests/{requestId}/reject")
    public ApiResponse<Void> rejectJoinRequest(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            HttpServletRequest request
    ) {
        groupJoinRequestService.rejectJoinRequest(request, groupId, requestId);
        return ApiResponse.success(null);
    }
}
