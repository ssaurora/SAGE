package com.sage.backend.execution.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class CreateJobRequest {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("workspace_id")
    private String workspaceId;

    @JsonProperty("attempt_no")
    private Integer attemptNo;

    @JsonProperty("capability_key")
    private String capabilityKey;

    @JsonProperty("provider_key")
    private String providerKey;

    @JsonProperty("runtime_profile")
    private String runtimeProfile;

    @JsonProperty("case_id")
    private String caseId;

    @JsonProperty("materialized_execution_graph")
    private JsonNode materializedExecutionGraph;

    @JsonProperty("runtime_assertions")
    private JsonNode runtimeAssertions;

    @JsonProperty("slot_bindings")
    private JsonNode slotBindings;

    @JsonProperty("args_draft")
    private JsonNode argsDraft;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public JsonNode getMaterializedExecutionGraph() {
        return materializedExecutionGraph;
    }

    public void setMaterializedExecutionGraph(JsonNode materializedExecutionGraph) {
        this.materializedExecutionGraph = materializedExecutionGraph;
    }

    public JsonNode getArgsDraft() {
        return argsDraft;
    }

    public void setArgsDraft(JsonNode argsDraft) {
        this.argsDraft = argsDraft;
    }

    public JsonNode getRuntimeAssertions() {
        return runtimeAssertions;
    }

    public void setRuntimeAssertions(JsonNode runtimeAssertions) {
        this.runtimeAssertions = runtimeAssertions;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
    }

    public String getCapabilityKey() {
        return capabilityKey;
    }

    public void setCapabilityKey(String capabilityKey) {
        this.capabilityKey = capabilityKey;
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

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public JsonNode getSlotBindings() {
        return slotBindings;
    }

    public void setSlotBindings(JsonNode slotBindings) {
        this.slotBindings = slotBindings;
    }
}
