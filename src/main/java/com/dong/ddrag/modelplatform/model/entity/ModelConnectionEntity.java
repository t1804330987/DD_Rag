package com.dong.ddrag.modelplatform.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

public class ModelConnectionEntity {
    private Long id;
    private String providerType;
    private String ownerType;
    private Long ownerUserId;
    private String name;
    private String baseUrl;
    private String apiKeyPlaintext;
    private String credentialStorageType;
    private Integer credentialVersion;
    private String maskedKeySuffix;
    private String providerOptionsJson;
    private Integer maxConcurrency;
    private String status;
    private Long configVersion;
    private String lastConnectionTestStatus;
    private LocalDateTime lastConnectionTestAt;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    @JsonIgnore
    public String readApiKeyPlaintext() { return apiKeyPlaintext; }
    public void setApiKeyPlaintext(String apiKeyPlaintext) { this.apiKeyPlaintext = apiKeyPlaintext; }
    public String getCredentialStorageType() { return credentialStorageType; }
    public void setCredentialStorageType(String credentialStorageType) { this.credentialStorageType = credentialStorageType; }
    public Integer getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(Integer credentialVersion) { this.credentialVersion = credentialVersion; }
    public String getMaskedKeySuffix() { return maskedKeySuffix; }
    public void setMaskedKeySuffix(String maskedKeySuffix) { this.maskedKeySuffix = maskedKeySuffix; }
    public String getProviderOptionsJson() { return providerOptionsJson; }
    public void setProviderOptionsJson(String providerOptionsJson) { this.providerOptionsJson = providerOptionsJson; }
    public Integer getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(Integer maxConcurrency) { this.maxConcurrency = maxConcurrency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getConfigVersion() { return configVersion; }
    public void setConfigVersion(Long configVersion) { this.configVersion = configVersion; }
    public String getLastConnectionTestStatus() { return lastConnectionTestStatus; }
    public void setLastConnectionTestStatus(String value) { this.lastConnectionTestStatus = value; }
    public LocalDateTime getLastConnectionTestAt() { return lastConnectionTestAt; }
    public void setLastConnectionTestAt(LocalDateTime value) { this.lastConnectionTestAt = value; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "ModelConnectionEntity{id=" + id + ", providerType='" + providerType + "', ownerType='"
                + ownerType + "', ownerUserId=" + ownerUserId + ", name='" + name + "', status='" + status
                + "', configVersion=" + configVersion + "}";
    }
}
