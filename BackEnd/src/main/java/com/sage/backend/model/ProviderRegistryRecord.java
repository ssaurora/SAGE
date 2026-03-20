package com.sage.backend.model;

import java.time.OffsetDateTime;

public class ProviderRegistryRecord {
    private String providerKey;
    private String capabilityKey;
    private String providerType;
    private String baseUrl;
    private String runtimeProfile;
    private Boolean enabled;
    private Integer priority;
    private OffsetDateTime createdAt;

    public String getProviderKey() { return providerKey; }
    public void setProviderKey(String providerKey) { this.providerKey = providerKey; }
    public String getCapabilityKey() { return capabilityKey; }
    public void setCapabilityKey(String capabilityKey) { this.capabilityKey = capabilityKey; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getRuntimeProfile() { return runtimeProfile; }
    public void setRuntimeProfile(String runtimeProfile) { this.runtimeProfile = runtimeProfile; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
