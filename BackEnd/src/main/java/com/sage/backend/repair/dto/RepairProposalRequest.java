package com.sage.backend.repair.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class RepairProposalRequest {
    @JsonProperty("waiting_context")
    private JsonNode waitingContext;

    @JsonProperty("validation_summary")
    private JsonNode validationSummary;

    @JsonProperty("failure_summary")
    private JsonNode failureSummary;

    @JsonProperty("user_note")
    private String userNote;

    public JsonNode getWaitingContext() {
        return waitingContext;
    }

    public void setWaitingContext(JsonNode waitingContext) {
        this.waitingContext = waitingContext;
    }

    public JsonNode getValidationSummary() {
        return validationSummary;
    }

    public void setValidationSummary(JsonNode validationSummary) {
        this.validationSummary = validationSummary;
    }

    public JsonNode getFailureSummary() {
        return failureSummary;
    }

    public void setFailureSummary(JsonNode failureSummary) {
        this.failureSummary = failureSummary;
    }

    public String getUserNote() {
        return userNote;
    }

    public void setUserNote(String userNote) {
        this.userNote = userNote;
    }
}
