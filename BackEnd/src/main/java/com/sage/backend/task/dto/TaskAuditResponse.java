package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaskAuditResponse {
    @JsonProperty("task_id")
    private String taskId;

    private List<AuditItem> items = new ArrayList<>();

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public List<AuditItem> getItems() {
        return items;
    }

    public void setItems(List<AuditItem> items) {
        this.items = items;
    }

    public static class AuditItem {
        private Long id;

        @JsonProperty("action_type")
        private String actionType;

        @JsonProperty("action_result")
        private String actionResult;

        @JsonProperty("trace_id")
        private String traceId;

        @JsonProperty("created_at")
        private String createdAt;

        private Map<String, Object> detail;

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

        public Map<String, Object> getDetail() {
            return detail;
        }

        public void setDetail(Map<String, Object> detail) {
            this.detail = detail;
        }

        public ContractGovernanceView getContractGovernance() {
            return contractGovernance;
        }

        public void setContractGovernance(ContractGovernanceView contractGovernance) {
            this.contractGovernance = contractGovernance;
        }
    }
}
