package com.dong.ddrag.modelplatform.model.entity;

import java.time.LocalDateTime;

public class ModelConnectionGrantEntity {
    private Long id; private Long connectionId; private String grantType; private Long granteeUserId; private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getConnectionId(){return connectionId;} public void setConnectionId(Long v){connectionId=v;}
    public String getGrantType(){return grantType;} public void setGrantType(String v){grantType=v;}
    public Long getGranteeUserId(){return granteeUserId;} public void setGranteeUserId(Long v){granteeUserId=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
