package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskManifestResponse {
    @JsonProperty("manifest_id")
    private String manifestId;

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("attempt_no")
    private Integer attemptNo;

    @JsonProperty("manifest_version")
    private Integer manifestVersion;

    @JsonProperty("goal_parse")
    private Object goalParse;

    @JsonProperty("skill_route")
    private Object skillRoute;

    @JsonProperty("logical_input_roles")
    private Object logicalInputRoles;

    @JsonProperty("slot_schema_view")
    private Object slotSchemaView;

    @JsonProperty("slot_bindings")
    private Object slotBindings;

    @JsonProperty("args_draft")
    private Object argsDraft;

    @JsonProperty("validation_summary")
    private Object validationSummary;

    @JsonProperty("execution_graph")
    private Object executionGraph;

    @JsonProperty("runtime_assertions")
    private Object runtimeAssertions;

    @JsonProperty("created_at")
    private String createdAt;

    public String getManifestId() { return manifestId; }
    public void setManifestId(String manifestId) { this.manifestId = manifestId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public Integer getManifestVersion() { return manifestVersion; }
    public void setManifestVersion(Integer manifestVersion) { this.manifestVersion = manifestVersion; }
    public Object getGoalParse() { return goalParse; }
    public void setGoalParse(Object goalParse) { this.goalParse = goalParse; }
    public Object getSkillRoute() { return skillRoute; }
    public void setSkillRoute(Object skillRoute) { this.skillRoute = skillRoute; }
    public Object getLogicalInputRoles() { return logicalInputRoles; }
    public void setLogicalInputRoles(Object logicalInputRoles) { this.logicalInputRoles = logicalInputRoles; }
    public Object getSlotSchemaView() { return slotSchemaView; }
    public void setSlotSchemaView(Object slotSchemaView) { this.slotSchemaView = slotSchemaView; }
    public Object getSlotBindings() { return slotBindings; }
    public void setSlotBindings(Object slotBindings) { this.slotBindings = slotBindings; }
    public Object getArgsDraft() { return argsDraft; }
    public void setArgsDraft(Object argsDraft) { this.argsDraft = argsDraft; }
    public Object getValidationSummary() { return validationSummary; }
    public void setValidationSummary(Object validationSummary) { this.validationSummary = validationSummary; }
    public Object getExecutionGraph() { return executionGraph; }
    public void setExecutionGraph(Object executionGraph) { this.executionGraph = executionGraph; }
    public Object getRuntimeAssertions() { return runtimeAssertions; }
    public void setRuntimeAssertions(Object runtimeAssertions) { this.runtimeAssertions = runtimeAssertions; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
