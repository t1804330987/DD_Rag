package com.dong.ddrag.modelplatform.model.entity;

import java.time.LocalDateTime;

public class AssistantInstructionProfileVersionEntity {
    private Long id; private Long profileId; private Integer version; private String content; private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getProfileId(){return profileId;} public void setProfileId(Long v){profileId=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public String getContent(){return content;} public void setContent(String v){content=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
