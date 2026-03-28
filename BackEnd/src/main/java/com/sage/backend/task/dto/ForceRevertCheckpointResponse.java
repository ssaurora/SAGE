package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ForceRevertCheckpointResponse {
    @JsonProperty("task_id")
    private String taskId;

    private String state;

    @JsonProperty("state_version")
    private Integer stateVersion;

    @JsonProperty("checkpoint_version")
    private Integer checkpointVersion;

    @JsonProperty("manifest_id")
    private String manifestId;

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

    public Integer getCheckpointVersion() {
        return checkpointVersion;
    }

    public void setCheckpointVersion(Integer checkpointVersion) {
        this.checkpointVersion = checkpointVersion;
    }

    public String getManifestId() {
        return manifestId;
    }

    public void setManifestId(String manifestId) {
        this.manifestId = manifestId;
    }
}
