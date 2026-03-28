package com.sage.backend.model;

import java.time.OffsetDateTime;

public class AnalysisManifest {
    private String manifestId;
    private String taskId;
    private Integer attemptNo;
    private Integer manifestVersion;
    private String goalParseJson;
    private String skillRouteJson;
    private String freezeStatus;
    private Integer planningRevision;
    private Integer checkpointVersion;
    private String graphDigest;
    private String planningSummaryJson;
    private String capabilityKey;
    private String selectedTemplate;
    private String templateVersion;
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
    public String getFreezeStatus() { return freezeStatus; }
    public void setFreezeStatus(String freezeStatus) { this.freezeStatus = freezeStatus; }
    public Integer getPlanningRevision() { return planningRevision; }
    public void setPlanningRevision(Integer planningRevision) { this.planningRevision = planningRevision; }
    public Integer getCheckpointVersion() { return checkpointVersion; }
    public void setCheckpointVersion(Integer checkpointVersion) { this.checkpointVersion = checkpointVersion; }
    public String getGraphDigest() { return graphDigest; }
    public void setGraphDigest(String graphDigest) { this.graphDigest = graphDigest; }
    public String getPlanningSummaryJson() { return planningSummaryJson; }
    public void setPlanningSummaryJson(String planningSummaryJson) { this.planningSummaryJson = planningSummaryJson; }
    public String getCapabilityKey() { return capabilityKey; }
    public void setCapabilityKey(String capabilityKey) { this.capabilityKey = capabilityKey; }
    public String getSelectedTemplate() { return selectedTemplate; }
    public void setSelectedTemplate(String selectedTemplate) { this.selectedTemplate = selectedTemplate; }
    public String getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(String templateVersion) { this.templateVersion = templateVersion; }
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
