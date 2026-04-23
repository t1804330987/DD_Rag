package com.dong.ddrag.groupmembership.controller;

import com.dong.ddrag.groupmembership.service.GroupMembershipService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupQueryController {

    private final GroupMembershipService groupMembershipService;

    public GroupQueryController(GroupMembershipService groupMembershipService) {
        this.groupMembershipService = groupMembershipService;
    }

    @GetMapping("/my")
    public GroupMembershipService.GroupQueryResult listVisibleGroups(HttpServletRequest request) {
        return groupMembershipService.listVisibleGroups(request);
    }
}
