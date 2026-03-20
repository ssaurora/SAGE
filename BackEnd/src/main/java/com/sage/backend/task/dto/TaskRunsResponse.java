package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class TaskRunsResponse {
    @JsonProperty("task_id")
    private String taskId;

    private final List<RunItem> items = new ArrayList<>();

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public List<RunItem> getItems() { return items; }

    public static class RunItem {
        @JsonProperty("attempt_no")
        private Integer attemptNo;
        @JsonProperty("job_id")
        private String jobId;
        @JsonProperty("workspace_id")
        private String workspaceId;
        @JsonProperty("job_state")
        private String jobState;
        @JsonProperty("workspace_state")
        private String workspaceState;
        @JsonProperty("result_bundle_id")
        private String resultBundleId;
        @JsonProperty("created_at")
        private String createdAt;
        @JsonProperty("finished_at")
        private String finishedAt;

        public Integer getAttemptNo() { return attemptNo; }
        public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }
        public String getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
        public String getJobState() { return jobState; }
        public void setJobState(String jobState) { this.jobState = jobState; }
        public String getWorkspaceState() { return workspaceState; }
        public void setWorkspaceState(String workspaceState) { this.workspaceState = workspaceState; }
        public String getResultBundleId() { return resultBundleId; }
        public void setResultBundleId(String resultBundleId) { this.resultBundleId = resultBundleId; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getFinishedAt() { return finishedAt; }
        public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }
    }
}
