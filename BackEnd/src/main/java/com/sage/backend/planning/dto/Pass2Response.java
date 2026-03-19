package com.sage.backend.planning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class Pass2Response {
    @JsonProperty("materialized_execution_graph")
    private JsonNode materializedExecutionGraph;

    @JsonProperty("runtime_assertions")
    private JsonNode runtimeAssertions;

    @JsonProperty("planning_summary")
    private JsonNode planningSummary;

    public JsonNode getMaterializedExecutionGraph() {
        return materializedExecutionGraph;
    }

    public void setMaterializedExecutionGraph(JsonNode materializedExecutionGraph) {
        this.materializedExecutionGraph = materializedExecutionGraph;
    }

    public JsonNode getRuntimeAssertions() {
        return runtimeAssertions;
    }

    public void setRuntimeAssertions(JsonNode runtimeAssertions) {
        this.runtimeAssertions = runtimeAssertions;
    }

    public JsonNode getPlanningSummary() {
        return planningSummary;
    }

    public void setPlanningSummary(JsonNode planningSummary) {
        this.planningSummary = planningSummary;
    }
}

