package com.sage.backend.execution.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class CreateJobRequest {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("materialized_execution_graph")
    private JsonNode materializedExecutionGraph;

    @JsonProperty("args_draft")
    private JsonNode argsDraft;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public JsonNode getMaterializedExecutionGraph() {
        return materializedExecutionGraph;
    }

    public void setMaterializedExecutionGraph(JsonNode materializedExecutionGraph) {
        this.materializedExecutionGraph = materializedExecutionGraph;
    }

    public JsonNode getArgsDraft() {
        return argsDraft;
    }

    public void setArgsDraft(JsonNode argsDraft) {
        this.argsDraft = argsDraft;
    }
}

