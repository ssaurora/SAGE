package com.sage.backend.model;

import java.time.OffsetDateTime;

public class TaskCatalogSnapshot {
    private Long id;
    private String taskId;
    private Integer inventoryVersion;
    private Integer catalogRevision;
    private String catalogFingerprint;
    private String catalogSummaryJson;
    private String catalogFactsJson;
    private String catalogSource;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getInventoryVersion() {
        return inventoryVersion;
    }

    public void setInventoryVersion(Integer inventoryVersion) {
        this.inventoryVersion = inventoryVersion;
    }

    public Integer getCatalogRevision() {
        return catalogRevision;
    }

    public void setCatalogRevision(Integer catalogRevision) {
        this.catalogRevision = catalogRevision;
    }

    public String getCatalogFingerprint() {
        return catalogFingerprint;
    }

    public void setCatalogFingerprint(String catalogFingerprint) {
        this.catalogFingerprint = catalogFingerprint;
    }

    public String getCatalogSummaryJson() {
        return catalogSummaryJson;
    }

    public void setCatalogSummaryJson(String catalogSummaryJson) {
        this.catalogSummaryJson = catalogSummaryJson;
    }

    public String getCatalogFactsJson() {
        return catalogFactsJson;
    }

    public void setCatalogFactsJson(String catalogFactsJson) {
        this.catalogFactsJson = catalogFactsJson;
    }

    public String getCatalogSource() {
        return catalogSource;
    }

    public void setCatalogSource(String catalogSource) {
        this.catalogSource = catalogSource;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
