package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateTaskResponse {
    @JsonProperty("task_id")
    private String taskId;

    private String state;

    @JsonProperty("state_version")
    private Integer stateVersion;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Integer getStateVersion() {
        return stateVersion;
    }

    public void setStateVersion(Integer stateVersion) {
        this.stateVersion = stateVersion;
    }
}

