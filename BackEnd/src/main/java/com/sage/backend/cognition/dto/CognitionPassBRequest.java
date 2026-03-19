package com.sage.backend.cognition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class CognitionPassBRequest {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("user_query")
    private String userQuery;

    @JsonProperty("state_version")
    private Integer stateVersion;

    @JsonProperty("pass1_result")
    private JsonNode pass1Result;

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

    public JsonNode getPass1Result() {
        return pass1Result;
    }

    public void setPass1Result(JsonNode pass1Result) {
        this.pass1Result = pass1Result;
    }
}

