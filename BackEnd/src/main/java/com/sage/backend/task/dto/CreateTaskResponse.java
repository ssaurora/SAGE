package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateTaskResponse {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("job_id")
    private String jobId;

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

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public Integer getStateVersion() {
        return stateVersion;
    }

    public void setStateVersion(Integer stateVersion) {
        this.stateVersion = stateVersion;
    }
}
