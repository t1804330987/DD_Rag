package com.dong.ddrag.modelplatform.model.entity;

import java.time.LocalDateTime;

public class AssistantInstructionProfileEntity {
    private Long id; private Long userId; private String name; private Long currentVersionId; private Boolean enabled; private Boolean isDefault;
    private LocalDateTime deletedAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public Long getCurrentVersionId(){return currentVersionId;} public void setCurrentVersionId(Long v){currentVersionId=v;}
    public Boolean getEnabled(){return enabled;} public void setEnabled(Boolean v){enabled=v;}
    public Boolean getDefault(){return isDefault;} public void setDefault(Boolean v){isDefault=v;}
    public LocalDateTime getDeletedAt(){return deletedAt;} public void setDeletedAt(LocalDateTime v){deletedAt=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
