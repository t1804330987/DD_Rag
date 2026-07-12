package com.dong.ddrag.modelplatform.model.entity;

import java.time.LocalDateTime;

public class AssistantTurnRequestEntity {
    private Long id; private Long userId; private String requestId; private String turnId; private Long sessionId;
    private Long userMessageId; private Long assistantMessageId; private String status; private String failureCode;
    private LocalDateTime createdAt; private LocalDateTime updatedAt; private LocalDateTime completedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getRequestId(){return requestId;} public void setRequestId(String v){requestId=v;} public String getTurnId(){return turnId;} public void setTurnId(String v){turnId=v;}
    public Long getSessionId(){return sessionId;} public void setSessionId(Long v){sessionId=v;} public Long getUserMessageId(){return userMessageId;} public void setUserMessageId(Long v){userMessageId=v;}
    public Long getAssistantMessageId(){return assistantMessageId;} public void setAssistantMessageId(Long v){assistantMessageId=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getFailureCode(){return failureCode;} public void setFailureCode(String v){failureCode=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;} public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime v){completedAt=v;}
}
