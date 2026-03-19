package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskResultResponse {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("task_state")
    private String taskState;

    @JsonProperty("job_state")
    private String jobState;

    @JsonProperty("result_bundle")
    private Object resultBundle;

    @JsonProperty("final_explanation")
    private Object finalExplanation;

    @JsonProperty("failure_summary")
    private Object failureSummary;

    @JsonProperty("docker_runtime_evidence")
    private Object dockerRuntimeEvidence;

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

    public String getTaskState() {
        return taskState;
    }

    public void setTaskState(String taskState) {
        this.taskState = taskState;
    }

    public String getJobState() {
        return jobState;
    }

    public void setJobState(String jobState) {
        this.jobState = jobState;
    }

    public Object getResultBundle() {
        return resultBundle;
    }

    public void setResultBundle(Object resultBundle) {
        this.resultBundle = resultBundle;
    }

    public Object getFinalExplanation() {
        return finalExplanation;
    }

    public void setFinalExplanation(Object finalExplanation) {
        this.finalExplanation = finalExplanation;
    }

    public Object getFailureSummary() {
        return failureSummary;
    }

    public void setFailureSummary(Object failureSummary) {
        this.failureSummary = failureSummary;
    }

    public Object getDockerRuntimeEvidence() {
        return dockerRuntimeEvidence;
    }

    public void setDockerRuntimeEvidence(Object dockerRuntimeEvidence) {
        this.dockerRuntimeEvidence = dockerRuntimeEvidence;
    }
}

