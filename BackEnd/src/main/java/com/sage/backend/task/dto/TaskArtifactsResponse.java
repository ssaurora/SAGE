package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class TaskArtifactsResponse {
    @JsonProperty("task_id")
    private String taskId;

    private final List<AttemptArtifacts> items = new ArrayList<>();

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public List<AttemptArtifacts> getItems() { return items; }

    public static class AttemptArtifacts {
        @JsonProperty("attempt_no")
        private Integer attemptNo;
        private WorkspaceSummary workspace;
        private ArtifactCatalog artifacts;

        public Integer getAttemptNo() { return attemptNo; }
        public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
        public WorkspaceSummary getWorkspace() { return workspace; }
        public void setWorkspace(WorkspaceSummary workspace) { this.workspace = workspace; }
        public ArtifactCatalog getArtifacts() { return artifacts; }
        public void setArtifacts(ArtifactCatalog artifacts) { this.artifacts = artifacts; }
    }

    public static class WorkspaceSummary {
        @JsonProperty("workspace_id")
        private String workspaceId;

        @JsonProperty("workspace_state")
        private String workspaceState;

        @JsonProperty("runtime_profile")
        private String runtimeProfile;

        @JsonProperty("container_name")
        private String containerName;

        @JsonProperty("host_workspace_path")
        private String hostWorkspacePath;

        @JsonProperty("archive_path")
        private String archivePath;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("started_at")
        private String startedAt;

        @JsonProperty("finished_at")
        private String finishedAt;

        @JsonProperty("cleaned_at")
        private String cleanedAt;

        @JsonProperty("archived_at")
        private String archivedAt;

        public String getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
        public String getWorkspaceState() { return workspaceState; }
        public void setWorkspaceState(String workspaceState) { this.workspaceState = workspaceState; }
        public String getRuntimeProfile() { return runtimeProfile; }
        public void setRuntimeProfile(String runtimeProfile) { this.runtimeProfile = runtimeProfile; }
        public String getContainerName() { return containerName; }
        public void setContainerName(String containerName) { this.containerName = containerName; }
        public String getHostWorkspacePath() { return hostWorkspacePath; }
        public void setHostWorkspacePath(String hostWorkspacePath) { this.hostWorkspacePath = hostWorkspacePath; }
        public String getArchivePath() { return archivePath; }
        public void setArchivePath(String archivePath) { this.archivePath = archivePath; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getStartedAt() { return startedAt; }
        public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
        public String getFinishedAt() { return finishedAt; }
        public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }
        public String getCleanedAt() { return cleanedAt; }
        public void setCleanedAt(String cleanedAt) { this.cleanedAt = cleanedAt; }
        public String getArchivedAt() { return archivedAt; }
        public void setArchivedAt(String archivedAt) { this.archivedAt = archivedAt; }
    }

    public static class ArtifactCatalog {
        @JsonProperty("primary_outputs")
        private List<ArtifactMeta> primaryOutputs;

        @JsonProperty("intermediate_outputs")
        private List<ArtifactMeta> intermediateOutputs;

        @JsonProperty("audit_artifacts")
        private List<ArtifactMeta> auditArtifacts;

        @JsonProperty("derived_outputs")
        private List<ArtifactMeta> derivedOutputs;

        private List<ArtifactMeta> logs;

        public List<ArtifactMeta> getPrimaryOutputs() { return primaryOutputs; }
        public void setPrimaryOutputs(List<ArtifactMeta> primaryOutputs) { this.primaryOutputs = primaryOutputs; }
        public List<ArtifactMeta> getIntermediateOutputs() { return intermediateOutputs; }
        public void setIntermediateOutputs(List<ArtifactMeta> intermediateOutputs) { this.intermediateOutputs = intermediateOutputs; }
        public List<ArtifactMeta> getAuditArtifacts() { return auditArtifacts; }
        public void setAuditArtifacts(List<ArtifactMeta> auditArtifacts) { this.auditArtifacts = auditArtifacts; }
        public List<ArtifactMeta> getDerivedOutputs() { return derivedOutputs; }
        public void setDerivedOutputs(List<ArtifactMeta> derivedOutputs) { this.derivedOutputs = derivedOutputs; }
        public List<ArtifactMeta> getLogs() { return logs; }
        public void setLogs(List<ArtifactMeta> logs) { this.logs = logs; }
    }

    public static class ArtifactMeta {
        @JsonProperty("artifact_id")
        private String artifactId;

        @JsonProperty("artifact_role")
        private String artifactRole;

        @JsonProperty("logical_name")
        private String logicalName;

        @JsonProperty("relative_path")
        private String relativePath;

        @JsonProperty("absolute_path")
        private String absolutePath;

        @JsonProperty("content_type")
        private String contentType;

        @JsonProperty("size_bytes")
        private Long sizeBytes;

        private String sha256;

        @JsonProperty("created_at")
        private String createdAt;

        public String getArtifactId() { return artifactId; }
        public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
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
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }
}
