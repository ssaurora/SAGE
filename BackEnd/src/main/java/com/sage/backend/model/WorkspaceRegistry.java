package com.sage.backend.model;

import java.time.OffsetDateTime;

public class WorkspaceRegistry {
    private String workspaceId;
    private String taskId;
    private String jobId;
    private Integer attemptNo;
    private String runtimeProfile;
    private String containerName;
    private String hostWorkspacePath;
    private String archivePath;
    private String workspaceState;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private OffsetDateTime cleanedAt;
    private OffsetDateTime archivedAt;
    private OffsetDateTime updatedAt;

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public String getRuntimeProfile() { return runtimeProfile; }
    public void setRuntimeProfile(String runtimeProfile) { this.runtimeProfile = runtimeProfile; }
    public String getContainerName() { return containerName; }
    public void setContainerName(String containerName) { this.containerName = containerName; }
    public String getHostWorkspacePath() { return hostWorkspacePath; }
    public void setHostWorkspacePath(String hostWorkspacePath) { this.hostWorkspacePath = hostWorkspacePath; }
    public String getArchivePath() { return archivePath; }
    public void setArchivePath(String archivePath) { this.archivePath = archivePath; }
    public String getWorkspaceState() { return workspaceState; }
    public void setWorkspaceState(String workspaceState) { this.workspaceState = workspaceState; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
    public OffsetDateTime getCleanedAt() { return cleanedAt; }
    public void setCleanedAt(OffsetDateTime cleanedAt) { this.cleanedAt = cleanedAt; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(OffsetDateTime archivedAt) { this.archivedAt = archivedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
