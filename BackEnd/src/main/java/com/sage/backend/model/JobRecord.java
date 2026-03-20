package com.sage.backend.model;

import java.time.OffsetDateTime;

public class JobRecord {
    private String jobId;
    private String taskId;
    private Integer attemptNo;
    private String jobState;
    private String executionGraphJson;
    private String runtimeAssertionsJson;
    private String planningPass2SummaryJson;
    private String resultObjectJson;
    private String resultBundleJson;
    private String finalExplanationJson;
    private String failureSummaryJson;
    private String dockerRuntimeEvidenceJson;
    private String workspaceSummaryJson;
    private String artifactCatalogJson;
    private String errorJson;
    private String workspaceId;
    private String providerKey;
    private String capabilityKey;
    private String runtimeProfile;
    private OffsetDateTime cancelRequestedAt;
    private OffsetDateTime cancelledAt;
    private String cancelReason;
    private OffsetDateTime acceptedAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private OffsetDateTime lastHeartbeatAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
    }

    public String getJobState() {
        return jobState;
    }

    public void setJobState(String jobState) {
        this.jobState = jobState;
    }

    public String getExecutionGraphJson() {
        return executionGraphJson;
    }

    public void setExecutionGraphJson(String executionGraphJson) {
        this.executionGraphJson = executionGraphJson;
    }

    public String getRuntimeAssertionsJson() {
        return runtimeAssertionsJson;
    }

    public void setRuntimeAssertionsJson(String runtimeAssertionsJson) {
        this.runtimeAssertionsJson = runtimeAssertionsJson;
    }

    public String getPlanningPass2SummaryJson() {
        return planningPass2SummaryJson;
    }

    public void setPlanningPass2SummaryJson(String planningPass2SummaryJson) {
        this.planningPass2SummaryJson = planningPass2SummaryJson;
    }

    public String getResultObjectJson() {
        return resultObjectJson;
    }

    public void setResultObjectJson(String resultObjectJson) {
        this.resultObjectJson = resultObjectJson;
    }

    public String getResultBundleJson() {
        return resultBundleJson;
    }

    public void setResultBundleJson(String resultBundleJson) {
        this.resultBundleJson = resultBundleJson;
    }

    public String getFinalExplanationJson() {
        return finalExplanationJson;
    }

    public void setFinalExplanationJson(String finalExplanationJson) {
        this.finalExplanationJson = finalExplanationJson;
    }

    public String getFailureSummaryJson() {
        return failureSummaryJson;
    }

    public void setFailureSummaryJson(String failureSummaryJson) {
        this.failureSummaryJson = failureSummaryJson;
    }

    public String getDockerRuntimeEvidenceJson() {
        return dockerRuntimeEvidenceJson;
    }

    public void setDockerRuntimeEvidenceJson(String dockerRuntimeEvidenceJson) {
        this.dockerRuntimeEvidenceJson = dockerRuntimeEvidenceJson;
    }

    public String getErrorJson() {
        return errorJson;
    }

    public void setErrorJson(String errorJson) {
        this.errorJson = errorJson;
    }

    public String getWorkspaceSummaryJson() {
        return workspaceSummaryJson;
    }

    public void setWorkspaceSummaryJson(String workspaceSummaryJson) {
        this.workspaceSummaryJson = workspaceSummaryJson;
    }

    public String getArtifactCatalogJson() {
        return artifactCatalogJson;
    }

    public void setArtifactCatalogJson(String artifactCatalogJson) {
        this.artifactCatalogJson = artifactCatalogJson;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getProviderKey() {
        return providerKey;
    }

    public void setProviderKey(String providerKey) {
        this.providerKey = providerKey;
    }

    public String getCapabilityKey() {
        return capabilityKey;
    }

    public void setCapabilityKey(String capabilityKey) {
        this.capabilityKey = capabilityKey;
    }

    public String getRuntimeProfile() {
        return runtimeProfile;
    }

    public void setRuntimeProfile(String runtimeProfile) {
        this.runtimeProfile = runtimeProfile;
    }

    public OffsetDateTime getCancelRequestedAt() {
        return cancelRequestedAt;
    }

    public void setCancelRequestedAt(OffsetDateTime cancelRequestedAt) {
        this.cancelRequestedAt = cancelRequestedAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(OffsetDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(OffsetDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public OffsetDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(OffsetDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
