package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaskContractResponse {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("current_contract_summary")
    private Map<String, Object> currentContractSummary;

    @JsonProperty("frozen_contract_summary")
    private Map<String, Object> frozenContractSummary;

    @JsonProperty("contract_governance")
    private ContractGovernanceView contractGovernance;

    @JsonProperty("active_manifest")
    private ManifestContractView activeManifest;

    @JsonProperty("audit_items")
    private List<AuditContractItem> auditItems = new ArrayList<>();

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Map<String, Object> getCurrentContractSummary() {
        return currentContractSummary;
    }

    public void setCurrentContractSummary(Map<String, Object> currentContractSummary) {
        this.currentContractSummary = currentContractSummary;
    }

    public Map<String, Object> getFrozenContractSummary() {
        return frozenContractSummary;
    }

    public void setFrozenContractSummary(Map<String, Object> frozenContractSummary) {
        this.frozenContractSummary = frozenContractSummary;
    }

    public ContractGovernanceView getContractGovernance() {
        return contractGovernance;
    }

    public void setContractGovernance(ContractGovernanceView contractGovernance) {
        this.contractGovernance = contractGovernance;
    }

    public ManifestContractView getActiveManifest() {
        return activeManifest;
    }

    public void setActiveManifest(ManifestContractView activeManifest) {
        this.activeManifest = activeManifest;
    }

    public List<AuditContractItem> getAuditItems() {
        return auditItems;
    }

    public void setAuditItems(List<AuditContractItem> auditItems) {
        this.auditItems = auditItems == null ? new ArrayList<>() : auditItems;
    }

    public static class ManifestContractView {
        @JsonProperty("manifest_id")
        private String manifestId;

        @JsonProperty("manifest_version")
        private Integer manifestVersion;

        @JsonProperty("freeze_status")
        private String freezeStatus;

        @JsonProperty("contract_summary")
        private Map<String, Object> contractSummary;

        public String getManifestId() {
            return manifestId;
        }

        public void setManifestId(String manifestId) {
            this.manifestId = manifestId;
        }

        public Integer getManifestVersion() {
            return manifestVersion;
        }

        public void setManifestVersion(Integer manifestVersion) {
            this.manifestVersion = manifestVersion;
        }

        public String getFreezeStatus() {
            return freezeStatus;
        }

        public void setFreezeStatus(String freezeStatus) {
            this.freezeStatus = freezeStatus;
        }

        public Map<String, Object> getContractSummary() {
            return contractSummary;
        }

        public void setContractSummary(Map<String, Object> contractSummary) {
            this.contractSummary = contractSummary;
        }
    }

    public static class AuditContractItem {
        private Long id;

        @JsonProperty("action_type")
        private String actionType;

        @JsonProperty("action_result")
        private String actionResult;

        @JsonProperty("trace_id")
        private String traceId;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("contract_governance")
        private ContractGovernanceView contractGovernance;

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

        public ContractGovernanceView getContractGovernance() {
            return contractGovernance;
        }

        public void setContractGovernance(ContractGovernanceView contractGovernance) {
            this.contractGovernance = contractGovernance;
        }
    }
}
