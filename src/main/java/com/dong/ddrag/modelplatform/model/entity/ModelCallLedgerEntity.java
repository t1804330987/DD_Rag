package com.dong.ddrag.modelplatform.model.entity;

import java.time.LocalDateTime;

public class ModelCallLedgerEntity {
    private Long id; private String invocationId; private Long userId; private String scenario; private Long sessionId;
    private Long userMessageId; private Long assistantMessageId; private String turnId; private String requestId;
    private Long connectionId; private Long modelId; private String providerTypeSnapshot; private String modelNameSnapshot;
    private String connectionNameSnapshot; private String ownerTypeSnapshot; private Long instructionProfileId;
    private Long instructionVersionId; private Integer instructionVersionSnapshot; private Long inputTokens; private Long outputTokens;
    private Long totalTokens; private Long durationMs; private String logicalStatus; private String transportStatus;
    private String errorCategory; private String errorSummary; private LocalDateTime startedAt; private LocalDateTime finishedAt;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getInvocationId(){return invocationId;} public void setInvocationId(String v){invocationId=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public String getScenario(){return scenario;} public void setScenario(String v){scenario=v;}
    public Long getSessionId(){return sessionId;} public void setSessionId(Long v){sessionId=v;} public Long getUserMessageId(){return userMessageId;} public void setUserMessageId(Long v){userMessageId=v;}
    public Long getAssistantMessageId(){return assistantMessageId;} public void setAssistantMessageId(Long v){assistantMessageId=v;} public String getTurnId(){return turnId;} public void setTurnId(String v){turnId=v;}
    public String getRequestId(){return requestId;} public void setRequestId(String v){requestId=v;} public Long getConnectionId(){return connectionId;} public void setConnectionId(Long v){connectionId=v;}
    public Long getModelId(){return modelId;} public void setModelId(Long v){modelId=v;} public String getProviderTypeSnapshot(){return providerTypeSnapshot;} public void setProviderTypeSnapshot(String v){providerTypeSnapshot=v;}
    public String getModelNameSnapshot(){return modelNameSnapshot;} public void setModelNameSnapshot(String v){modelNameSnapshot=v;} public String getConnectionNameSnapshot(){return connectionNameSnapshot;} public void setConnectionNameSnapshot(String v){connectionNameSnapshot=v;}
    public String getOwnerTypeSnapshot(){return ownerTypeSnapshot;} public void setOwnerTypeSnapshot(String v){ownerTypeSnapshot=v;} public Long getInstructionProfileId(){return instructionProfileId;} public void setInstructionProfileId(Long v){instructionProfileId=v;}
    public Long getInstructionVersionId(){return instructionVersionId;} public void setInstructionVersionId(Long v){instructionVersionId=v;} public Integer getInstructionVersionSnapshot(){return instructionVersionSnapshot;} public void setInstructionVersionSnapshot(Integer v){instructionVersionSnapshot=v;}
    public Long getInputTokens(){return inputTokens;} public void setInputTokens(Long v){inputTokens=v;} public Long getOutputTokens(){return outputTokens;} public void setOutputTokens(Long v){outputTokens=v;}
    public Long getTotalTokens(){return totalTokens;} public void setTotalTokens(Long v){totalTokens=v;} public Long getDurationMs(){return durationMs;} public void setDurationMs(Long v){durationMs=v;}
    public String getLogicalStatus(){return logicalStatus;} public void setLogicalStatus(String v){logicalStatus=v;} public String getTransportStatus(){return transportStatus;} public void setTransportStatus(String v){transportStatus=v;}
    public String getErrorCategory(){return errorCategory;} public void setErrorCategory(String v){errorCategory=v;} public String getErrorSummary(){return errorSummary;} public void setErrorSummary(String v){errorSummary=v;}
    public LocalDateTime getStartedAt(){return startedAt;} public void setStartedAt(LocalDateTime v){startedAt=v;} public LocalDateTime getFinishedAt(){return finishedAt;} public void setFinishedAt(LocalDateTime v){finishedAt=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}

    @Override
    public String toString() {
        return "ModelCallLedgerEntity{id=" + id + ", invocationId='" + invocationId + "', userId=" + userId
                + ", scenario='" + scenario + "', providerTypeSnapshot='" + providerTypeSnapshot
                + "', modelNameSnapshot='" + modelNameSnapshot + "', logicalStatus='" + logicalStatus
                + "', transportStatus='" + transportStatus + "'}";
    }
}
