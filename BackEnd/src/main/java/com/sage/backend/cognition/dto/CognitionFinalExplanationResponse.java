package com.sage.backend.cognition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class CognitionFinalExplanationResponse {
    private Boolean available;
    private String title;
    private List<String> highlights;
    private String narrative;

    @JsonProperty("generated_at")
    private String generatedAt;

    @JsonProperty("failure_code")
    private String failureCode;

    @JsonProperty("failure_message")
    private String failureMessage;

    @JsonProperty("cognition_metadata")
    private Map<String, Object> cognitionMetadata;

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(List<String> highlights) {
        this.highlights = highlights;
    }

    public String getNarrative() {
        return narrative;
    }

    public void setNarrative(String narrative) {
        this.narrative = narrative;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public Map<String, Object> getCognitionMetadata() {
        return cognitionMetadata;
    }

    public void setCognitionMetadata(Map<String, Object> cognitionMetadata) {
        this.cognitionMetadata = cognitionMetadata;
    }
}
