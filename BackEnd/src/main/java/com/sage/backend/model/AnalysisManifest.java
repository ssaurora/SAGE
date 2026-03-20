package com.sage.backend.model;

import java.time.OffsetDateTime;

public class AnalysisManifest {
    private String manifestId;
    private String taskId;
    private Integer attemptNo;
    private Integer manifestVersion;
    private String goalParseJson;
    private String skillRouteJson;
    private String logicalInputRolesJson;
    private String slotSchemaViewJson;
    private String slotBindingsJson;
    private String argsDraftJson;
    private String validationSummaryJson;
    private String executionGraphJson;
    private String runtimeAssertionsJson;
    private OffsetDateTime createdAt;

    public String getManifestId() { return manifestId; }
    public void setManifestId(String manifestId) { this.manifestId = manifestId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public Integer getManifestVersion() { return manifestVersion; }
    public void setManifestVersion(Integer manifestVersion) { this.manifestVersion = manifestVersion; }
    public String getGoalParseJson() { return goalParseJson; }
    public void setGoalParseJson(String goalParseJson) { this.goalParseJson = goalParseJson; }
    public String getSkillRouteJson() { return skillRouteJson; }
    public void setSkillRouteJson(String skillRouteJson) { this.skillRouteJson = skillRouteJson; }
    public String getLogicalInputRolesJson() { return logicalInputRolesJson; }
    public void setLogicalInputRolesJson(String logicalInputRolesJson) { this.logicalInputRolesJson = logicalInputRolesJson; }
    public String getSlotSchemaViewJson() { return slotSchemaViewJson; }
    public void setSlotSchemaViewJson(String slotSchemaViewJson) { this.slotSchemaViewJson = slotSchemaViewJson; }
    public String getSlotBindingsJson() { return slotBindingsJson; }
    public void setSlotBindingsJson(String slotBindingsJson) { this.slotBindingsJson = slotBindingsJson; }
    public String getArgsDraftJson() { return argsDraftJson; }
    public void setArgsDraftJson(String argsDraftJson) { this.argsDraftJson = argsDraftJson; }
    public String getValidationSummaryJson() { return validationSummaryJson; }
    public void setValidationSummaryJson(String validationSummaryJson) { this.validationSummaryJson = validationSummaryJson; }
    public String getExecutionGraphJson() { return executionGraphJson; }
    public void setExecutionGraphJson(String executionGraphJson) { this.executionGraphJson = executionGraphJson; }
    public String getRuntimeAssertionsJson() { return runtimeAssertionsJson; }
    public void setRuntimeAssertionsJson(String runtimeAssertionsJson) { this.runtimeAssertionsJson = runtimeAssertionsJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
