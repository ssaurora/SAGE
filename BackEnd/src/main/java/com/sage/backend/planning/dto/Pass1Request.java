package com.sage.backend.planning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Pass1Request {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("user_query")
    private String userQuery;

    @JsonProperty("state_version")
    private Integer stateVersion;

    @JsonProperty("capability_key")
    private String capabilityKey;

    @JsonProperty("selected_template")
    private String selectedTemplate;

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

    public String getCapabilityKey() {
        return capabilityKey;
    }

    public void setCapabilityKey(String capabilityKey) {
        this.capabilityKey = capabilityKey;
    }

    public String getSelectedTemplate() {
        return selectedTemplate;
    }

    public void setSelectedTemplate(String selectedTemplate) {
        this.selectedTemplate = selectedTemplate;
    }
}
