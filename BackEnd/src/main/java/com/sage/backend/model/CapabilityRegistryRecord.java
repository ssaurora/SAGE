package com.sage.backend.model;

import java.time.OffsetDateTime;

public class CapabilityRegistryRecord {
    private String capabilityKey;
    private String displayName;
    private String skillName;
    private Boolean enabled;
    private String defaultProviderKey;
    private OffsetDateTime createdAt;

    public String getCapabilityKey() { return capabilityKey; }
    public void setCapabilityKey(String capabilityKey) { this.capabilityKey = capabilityKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getDefaultProviderKey() { return defaultProviderKey; }
    public void setDefaultProviderKey(String defaultProviderKey) { this.defaultProviderKey = defaultProviderKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
