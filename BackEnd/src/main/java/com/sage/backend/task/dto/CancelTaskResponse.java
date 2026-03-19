package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CancelTaskResponse {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("job_id")
    private String jobId;

    private String state;

    @JsonProperty("job_state")
    private String jobState;

    private Boolean accepted;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getJobState() {
        return jobState;
    }

    public void setJobState(String jobState) {
        this.jobState = jobState;
    }

    public Boolean getAccepted() {
        return accepted;
    }

    public void setAccepted(Boolean accepted) {
        this.accepted = accepted;
    }
}

