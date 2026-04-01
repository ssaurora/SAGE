package com.sage.backend.repair.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RepairProposalResponse {
    private Boolean available;

    @JsonProperty("user_facing_reason")
    private String userFacingReason;

    @JsonProperty("resume_hint")
    private String resumeHint;

    @JsonProperty("action_explanations")
    private List<ActionExplanation> actionExplanations = new ArrayList<>();

    @JsonProperty("notes")
    private List<String> notes = new ArrayList<>();

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

    public String getUserFacingReason() {
        return userFacingReason;
    }

    public void setUserFacingReason(String userFacingReason) {
        this.userFacingReason = userFacingReason;
    }

    public String getResumeHint() {
        return resumeHint;
    }

    public void setResumeHint(String resumeHint) {
        this.resumeHint = resumeHint;
    }

    public List<ActionExplanation> getActionExplanations() {
        return actionExplanations;
    }

    public void setActionExplanations(List<ActionExplanation> actionExplanations) {
        this.actionExplanations = actionExplanations == null ? new ArrayList<>() : actionExplanations;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes == null ? new ArrayList<>() : notes;
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

    public static class ActionExplanation {
        private String key;
        private String message;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
