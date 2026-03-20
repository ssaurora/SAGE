package com.sage.backend.model;

import java.time.OffsetDateTime;

public class ResultBundleRecord {
    private String resultBundleId;
    private String taskId;
    private String jobId;
    private Integer attemptNo;
    private String manifestId;
    private String workspaceId;
    private String resultBundleJson;
    private String finalExplanationJson;
    private String summaryText;
    private OffsetDateTime createdAt;

    public String getResultBundleId() { return resultBundleId; }
    public void setResultBundleId(String resultBundleId) { this.resultBundleId = resultBundleId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public String getManifestId() { return manifestId; }
    public void setManifestId(String manifestId) { this.manifestId = manifestId; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getResultBundleJson() { return resultBundleJson; }
    public void setResultBundleJson(String resultBundleJson) { this.resultBundleJson = resultBundleJson; }
    public String getFinalExplanationJson() { return finalExplanationJson; }
    public void setFinalExplanationJson(String finalExplanationJson) { this.finalExplanationJson = finalExplanationJson; }
    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
