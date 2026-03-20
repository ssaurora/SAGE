package com.sage.backend.model;

import java.time.OffsetDateTime;

public class ArtifactIndexRecord {
    private String artifactId;
    private String taskId;
    private String jobId;
    private Integer attemptNo;
    private String workspaceId;
    private String resultBundleId;
    private String artifactRole;
    private String logicalName;
    private String relativePath;
    private String absolutePath;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private OffsetDateTime createdAt;

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getResultBundleId() { return resultBundleId; }
    public void setResultBundleId(String resultBundleId) { this.resultBundleId = resultBundleId; }
    public String getArtifactRole() { return artifactRole; }
    public void setArtifactRole(String artifactRole) { this.artifactRole = artifactRole; }
    public String getLogicalName() { return logicalName; }
    public void setLogicalName(String logicalName) { this.logicalName = logicalName; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public String getAbsolutePath() { return absolutePath; }
    public void setAbsolutePath(String absolutePath) { this.absolutePath = absolutePath; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
