package com.dong.ddrag.modelplatform.model.entity;

import java.time.LocalDateTime;

public class ModelScenarioRouteEntity {
    private Long id; private String scenario; private Long connectionId; private Long modelId; private Boolean enabled;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getScenario(){return scenario;} public void setScenario(String v){scenario=v;}
    public Long getConnectionId(){return connectionId;} public void setConnectionId(Long v){connectionId=v;}
    public Long getModelId(){return modelId;} public void setModelId(Long v){modelId=v;}
    public Boolean getEnabled(){return enabled;} public void setEnabled(Boolean v){enabled=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
