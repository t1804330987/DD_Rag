package com.dong.ddrag.groupmembership.model.vo;

public record GroupMemberVO(
        Long userId,
        String userCode,
        String displayName,
        String role
) {
}
