package com.dong.ddrag.modelplatform.model.entity;

import java.time.LocalDateTime;

public class ModelConnectionModelEntity {
    private Long id;
    private Long connectionId;
    private String modelName;
    private String sourceType;
    private String capabilitiesJson;
    private String testStatus;
    private Long testedConfigVersion;
    private LocalDateTime lastTestAt;
    private Boolean enabled;
    private LocalDateTime hiddenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; } public void setId(Long v) { id=v; }
    public Long getConnectionId() { return connectionId; } public void setConnectionId(Long v) { connectionId=v; }
    public String getModelName() { return modelName; } public void setModelName(String v) { modelName=v; }
    public String getSourceType() { return sourceType; } public void setSourceType(String v) { sourceType=v; }
    public String getCapabilitiesJson() { return capabilitiesJson; } public void setCapabilitiesJson(String v) { capabilitiesJson=v; }
    public String getTestStatus() { return testStatus; } public void setTestStatus(String v) { testStatus=v; }
    public Long getTestedConfigVersion() { return testedConfigVersion; } public void setTestedConfigVersion(Long v) { testedConfigVersion=v; }
    public LocalDateTime getLastTestAt() { return lastTestAt; } public void setLastTestAt(LocalDateTime v) { lastTestAt=v; }
    public Boolean getEnabled() { return enabled; } public void setEnabled(Boolean v) { enabled=v; }
    public LocalDateTime getHiddenAt() { return hiddenAt; } public void setHiddenAt(LocalDateTime v) { hiddenAt=v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt=v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime v) { updatedAt=v; }
}
