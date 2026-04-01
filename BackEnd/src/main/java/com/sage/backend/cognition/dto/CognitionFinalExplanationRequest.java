package com.sage.backend.cognition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class CognitionFinalExplanationRequest {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("user_query")
    private String userQuery;

    @JsonProperty("case_id")
    private String caseId;

    @JsonProperty("provider_key")
    private String providerKey;

    @JsonProperty("runtime_profile")
    private String runtimeProfile;

    @JsonProperty("result_bundle")
    private JsonNode resultBundle;

    @JsonProperty("artifact_catalog")
    private JsonNode artifactCatalog;

    @JsonProperty("docker_runtime_evidence")
    private JsonNode dockerRuntimeEvidence;

    @JsonProperty("workspace_summary")
    private JsonNode workspaceSummary;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getProviderKey() {
        return providerKey;
    }

    public void setProviderKey(String providerKey) {
        this.providerKey = providerKey;
    }

    public String getRuntimeProfile() {
        return runtimeProfile;
    }

    public void setRuntimeProfile(String runtimeProfile) {
        this.runtimeProfile = runtimeProfile;
    }

    public JsonNode getResultBundle() {
        return resultBundle;
    }

    public void setResultBundle(JsonNode resultBundle) {
        this.resultBundle = resultBundle;
    }

    public JsonNode getArtifactCatalog() {
        return artifactCatalog;
    }

    public void setArtifactCatalog(JsonNode artifactCatalog) {
        this.artifactCatalog = artifactCatalog;
    }

    public JsonNode getDockerRuntimeEvidence() {
        return dockerRuntimeEvidence;
    }

    public void setDockerRuntimeEvidence(JsonNode dockerRuntimeEvidence) {
        this.dockerRuntimeEvidence = dockerRuntimeEvidence;
    }

    public JsonNode getWorkspaceSummary() {
        return workspaceSummary;
    }

    public void setWorkspaceSummary(JsonNode workspaceSummary) {
        this.workspaceSummary = workspaceSummary;
    }
}
