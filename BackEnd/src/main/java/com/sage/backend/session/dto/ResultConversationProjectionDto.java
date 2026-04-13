package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sage.backend.task.dto.TaskResultResponse;

public class ResultConversationProjectionDto {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("task_state")
    private String taskState;

    @JsonProperty("result_bundle")
    private TaskResultResponse.ResultBundle resultBundle;

    @JsonProperty("final_explanation")
    private TaskResultResponse.FinalExplanation finalExplanation;

    @JsonProperty("latest_result_bundle_id")
    private String latestResultBundleId;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskState() {
        return taskState;
    }

    public void setTaskState(String taskState) {
        this.taskState = taskState;
    }

    public TaskResultResponse.ResultBundle getResultBundle() {
        return resultBundle;
    }

    public void setResultBundle(TaskResultResponse.ResultBundle resultBundle) {
        this.resultBundle = resultBundle;
    }

    public TaskResultResponse.FinalExplanation getFinalExplanation() {
        return finalExplanation;
    }

    public void setFinalExplanation(TaskResultResponse.FinalExplanation finalExplanation) {
        this.finalExplanation = finalExplanation;
    }

    public String getLatestResultBundleId() {
        return latestResultBundleId;
    }

    public void setLatestResultBundleId(String latestResultBundleId) {
        this.latestResultBundleId = latestResultBundleId;
    }
}
