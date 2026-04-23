package com.dong.ddrag.groupmembership.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateGroupRequest {

    @NotBlank(message = "组名称不能为空")
    @Size(max = 128, message = "组名称不能超过 128")
    private String name;

    @Size(max = 512, message = "组描述不能超过 512")
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
