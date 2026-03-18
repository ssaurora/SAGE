package com.sage.backend.planning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Pass1Request {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("user_query")
    private String userQuery;

    @JsonProperty("state_version")
    private Integer stateVersion;

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
}

