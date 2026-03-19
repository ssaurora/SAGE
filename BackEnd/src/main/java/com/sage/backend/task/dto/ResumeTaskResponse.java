package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ResumeTaskResponse {
    @JsonProperty("task_id")
    private String taskId;

    private String state;

    @JsonProperty("state_version")
    private Integer stateVersion;

    @JsonProperty("resume_accepted")
    private Boolean resumeAccepted;

    @JsonProperty("resume_attempt")
    private Integer resumeAttempt;

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

    public Boolean getResumeAccepted() {
        return resumeAccepted;
    }

    public void setResumeAccepted(Boolean resumeAccepted) {
        this.resumeAccepted = resumeAccepted;
    }

    public Integer getResumeAttempt() {
        return resumeAttempt;
    }

    public void setResumeAttempt(Integer resumeAttempt) {
        this.resumeAttempt = resumeAttempt;
    }
}

