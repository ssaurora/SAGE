package com.sage.backend.repair.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class RepairProposalResponse {
    @JsonProperty("user_facing_reason")
    private String userFacingReason;

    @JsonProperty("resume_hint")
    private String resumeHint;

    @JsonProperty("action_explanations")
    private List<ActionExplanation> actionExplanations = new ArrayList<>();

    @JsonProperty("notes")
    private List<String> notes = new ArrayList<>();

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
