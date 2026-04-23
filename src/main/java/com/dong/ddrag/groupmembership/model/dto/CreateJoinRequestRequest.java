package com.dong.ddrag.groupmembership.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateJoinRequestRequest {

    @NotBlank(message = "组织 ID 不能为空")
    @Size(max = 80, message = "组织 ID 不能超过 80")
    private String groupCode;

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }
}
