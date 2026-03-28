package com.sage.backend.planning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class Pass2Response {
    @JsonProperty("materialized_execution_graph")
    private JsonNode materializedExecutionGraph;

    @JsonProperty("runtime_assertions")
    private JsonNode runtimeAssertions;

    @JsonProperty("graph_digest")
    private String graphDigest;

    @JsonProperty("planning_summary")
    private JsonNode planningSummary;

    @JsonProperty("canonicalization_summary")
    private JsonNode canonicalizationSummary;

    @JsonProperty("rewrite_summary")
    private JsonNode rewriteSummary;

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

    public String getGraphDigest() {
        return graphDigest;
    }

    public void setGraphDigest(String graphDigest) {
        this.graphDigest = graphDigest;
    }

    public JsonNode getCanonicalizationSummary() {
        return canonicalizationSummary;
    }

    public void setCanonicalizationSummary(JsonNode canonicalizationSummary) {
        this.canonicalizationSummary = canonicalizationSummary;
    }

    public JsonNode getRewriteSummary() {
        return rewriteSummary;
    }

    public void setRewriteSummary(JsonNode rewriteSummary) {
        this.rewriteSummary = rewriteSummary;
    }
}
