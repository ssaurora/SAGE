package com.sage.backend.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SceneAuditSummaryDTO {
    @JsonProperty("latest_audit_at")
    private String latestAuditAt;

    @JsonProperty("latest_audit_action_type")
    private String latestAuditActionType;

    @JsonProperty("latest_audit_action_result")
    private String latestAuditActionResult;

    public String getLatestAuditAt() {
        return latestAuditAt;
    }

    public void setLatestAuditAt(String latestAuditAt) {
        this.latestAuditAt = latestAuditAt;
    }

    public String getLatestAuditActionType() {
        return latestAuditActionType;
    }

    public void setLatestAuditActionType(String latestAuditActionType) {
        this.latestAuditActionType = latestAuditActionType;
    }

    public String getLatestAuditActionResult() {
        return latestAuditActionResult;
    }

    public void setLatestAuditActionResult(String latestAuditActionResult) {
        this.latestAuditActionResult = latestAuditActionResult;
    }
}
