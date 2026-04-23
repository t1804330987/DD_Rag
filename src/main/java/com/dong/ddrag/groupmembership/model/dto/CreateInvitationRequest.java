package com.dong.ddrag.groupmembership.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class CreateInvitationRequest {

    @NotNull(message = "被邀请用户不能为空")
    @Positive(message = "被邀请用户非法")
    private Long inviteeUserId;

    public Long getInviteeUserId() {
        return inviteeUserId;
    }

    public void setInviteeUserId(Long inviteeUserId) {
        this.inviteeUserId = inviteeUserId;
    }
}
