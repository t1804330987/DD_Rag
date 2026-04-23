package com.dong.ddrag.groupmembership.model.vo;

import java.time.LocalDateTime;

public record MyJoinRequestVO(
        Long requestId,
        Long groupId,
        String groupCode,
        String groupName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {
}
