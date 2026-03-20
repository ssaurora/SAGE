package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class TaskArtifactsResponse {
    @JsonProperty("task_id")
    private String taskId;

    private final List<AttemptArtifacts> items = new ArrayList<>();

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public List<AttemptArtifacts> getItems() { return items; }

    public static class AttemptArtifacts {
        @JsonProperty("attempt_no")
        private Integer attemptNo;
        private Object workspace;
        private Object artifacts;

        public Integer getAttemptNo() { return attemptNo; }
        public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
        public Object getWorkspace() { return workspace; }
        public void setWorkspace(Object workspace) { this.workspace = workspace; }
        public Object getArtifacts() { return artifacts; }
        public void setArtifacts(Object artifacts) { this.artifacts = artifacts; }
    }
}
