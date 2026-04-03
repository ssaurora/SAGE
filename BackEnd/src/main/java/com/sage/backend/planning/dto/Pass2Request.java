package com.sage.backend.planning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class Pass2Request {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("state_version")
    private Integer stateVersion;

    @JsonProperty("pass1_result")
    private JsonNode pass1Result;

    @JsonProperty("passb_result")
    private JsonNode passbResult;

    @JsonProperty("validation_summary")
    private JsonNode validationSummary;

    @JsonProperty("metadata_catalog_facts")
    private JsonNode metadataCatalogFacts;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getStateVersion() {
        return stateVersion;
    }

    public void setStateVersion(Integer stateVersion) {
        this.stateVersion = stateVersion;
    }

    public JsonNode getPass1Result() {
        return pass1Result;
    }

    public void setPass1Result(JsonNode pass1Result) {
        this.pass1Result = pass1Result;
    }

    public JsonNode getPassbResult() {
        return passbResult;
    }

    public void setPassbResult(JsonNode passbResult) {
        this.passbResult = passbResult;
    }

    public JsonNode getValidationSummary() {
        return validationSummary;
    }

    public void setValidationSummary(JsonNode validationSummary) {
        this.validationSummary = validationSummary;
    }

    public JsonNode getMetadataCatalogFacts() {
        return metadataCatalogFacts;
    }

    public void setMetadataCatalogFacts(JsonNode metadataCatalogFacts) {
        this.metadataCatalogFacts = metadataCatalogFacts;
    }
}
