package com.sage.backend.cognition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CognitionGoalRouteRequest {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("user_query")
    private String userQuery;

    @JsonProperty("state_version")
    private Integer stateVersion;

    @JsonProperty("user_note")
    private String userNote;

    @JsonProperty("allowed_capabilities")
    private List<String> allowedCapabilities;

    @JsonProperty("allowed_templates")
    private List<String> allowedTemplates;

    @JsonProperty("known_cases")
    private List<String> knownCases;

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

    public Integer getStateVersion() {
        return stateVersion;
    }

    public void setStateVersion(Integer stateVersion) {
        this.stateVersion = stateVersion;
    }

    public String getUserNote() {
        return userNote;
    }

    public void setUserNote(String userNote) {
        this.userNote = userNote;
    }

    public List<String> getAllowedCapabilities() {
        return allowedCapabilities;
    }

    public void setAllowedCapabilities(List<String> allowedCapabilities) {
        this.allowedCapabilities = allowedCapabilities;
    }

    public List<String> getAllowedTemplates() {
        return allowedTemplates;
    }

    public void setAllowedTemplates(List<String> allowedTemplates) {
        this.allowedTemplates = allowedTemplates;
    }

    public List<String> getKnownCases() {
        return knownCases;
    }

    public void setKnownCases(List<String> knownCases) {
        this.knownCases = knownCases;
    }
}
