package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaskCatalogResponse {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("inventory_version")
    private Integer inventoryVersion;

    @JsonProperty("catalog_summary")
    private Map<String, Object> catalogSummary;

    @JsonProperty("catalog_facts")
    private List<Map<String, Object>> catalogFacts = new ArrayList<>();

    @JsonProperty("catalog_governance")
    private CatalogGovernanceView catalogGovernance;

    @JsonProperty("latest_snapshot")
    private SnapshotView latestSnapshot;

    @JsonProperty("audit_items")
    private List<AuditCatalogItem> auditItems = new ArrayList<>();

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

    public Map<String, Object> getCatalogSummary() {
        return catalogSummary;
    }

    public void setCatalogSummary(Map<String, Object> catalogSummary) {
        this.catalogSummary = catalogSummary;
    }

    public List<Map<String, Object>> getCatalogFacts() {
        return catalogFacts;
    }

    public void setCatalogFacts(List<Map<String, Object>> catalogFacts) {
        this.catalogFacts = catalogFacts == null ? new ArrayList<>() : catalogFacts;
    }

    public CatalogGovernanceView getCatalogGovernance() {
        return catalogGovernance;
    }

    public void setCatalogGovernance(CatalogGovernanceView catalogGovernance) {
        this.catalogGovernance = catalogGovernance;
    }

    public SnapshotView getLatestSnapshot() {
        return latestSnapshot;
    }

    public void setLatestSnapshot(SnapshotView latestSnapshot) {
        this.latestSnapshot = latestSnapshot;
    }

    public List<AuditCatalogItem> getAuditItems() {
        return auditItems;
    }

    public void setAuditItems(List<AuditCatalogItem> auditItems) {
        this.auditItems = auditItems == null ? new ArrayList<>() : auditItems;
    }

    public static class SnapshotView {
        private Long id;

        @JsonProperty("inventory_version")
        private Integer inventoryVersion;

        @JsonProperty("catalog_revision")
        private Integer catalogRevision;

        @JsonProperty("catalog_fingerprint")
        private String catalogFingerprint;

        @JsonProperty("catalog_source")
        private String catalogSource;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("catalog_summary")
        private Map<String, Object> catalogSummary;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
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

        public String getCatalogSource() {
            return catalogSource;
        }

        public void setCatalogSource(String catalogSource) {
            this.catalogSource = catalogSource;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public Map<String, Object> getCatalogSummary() {
            return catalogSummary;
        }

        public void setCatalogSummary(Map<String, Object> catalogSummary) {
            this.catalogSummary = catalogSummary;
        }
    }

    public static class AuditCatalogItem {
        private Long id;

        @JsonProperty("action_type")
        private String actionType;

        @JsonProperty("action_result")
        private String actionResult;

        @JsonProperty("trace_id")
        private String traceId;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("catalog_governance")
        private CatalogGovernanceView catalogGovernance;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getActionType() {
            return actionType;
        }

        public void setActionType(String actionType) {
            this.actionType = actionType;
        }

        public String getActionResult() {
            return actionResult;
        }

        public void setActionResult(String actionResult) {
            this.actionResult = actionResult;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public CatalogGovernanceView getCatalogGovernance() {
            return catalogGovernance;
        }

        public void setCatalogGovernance(CatalogGovernanceView catalogGovernance) {
            this.catalogGovernance = catalogGovernance;
        }
    }
}
