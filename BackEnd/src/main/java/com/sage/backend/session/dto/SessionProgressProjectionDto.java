package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SessionProgressProjectionDto {
    @JsonProperty("current_phase_label")
    private String currentPhaseLabel;

    @JsonProperty("current_system_action")
    private String currentSystemAction;

    @JsonProperty("latest_progress_note")
    private String latestProgressNote;

    @JsonProperty("estimated_next_milestone")
    private String estimatedNextMilestone;

    @JsonProperty("related_task_id")
    private String relatedTaskId;

    @JsonProperty("related_result_bundle_id")
    private String relatedResultBundleId;

    public String getCurrentPhaseLabel() {
        return currentPhaseLabel;
    }

    public void setCurrentPhaseLabel(String currentPhaseLabel) {
        this.currentPhaseLabel = currentPhaseLabel;
    }

    public String getCurrentSystemAction() {
        return currentSystemAction;
    }

    public void setCurrentSystemAction(String currentSystemAction) {
        this.currentSystemAction = currentSystemAction;
    }

    public String getLatestProgressNote() {
        return latestProgressNote;
    }

    public void setLatestProgressNote(String latestProgressNote) {
        this.latestProgressNote = latestProgressNote;
    }

    public String getEstimatedNextMilestone() {
        return estimatedNextMilestone;
    }

    public void setEstimatedNextMilestone(String estimatedNextMilestone) {
        this.estimatedNextMilestone = estimatedNextMilestone;
    }

    public String getRelatedTaskId() {
        return relatedTaskId;
    }

    public void setRelatedTaskId(String relatedTaskId) {
        this.relatedTaskId = relatedTaskId;
    }

    public String getRelatedResultBundleId() {
        return relatedResultBundleId;
    }

    public void setRelatedResultBundleId(String relatedResultBundleId) {
        this.relatedResultBundleId = relatedResultBundleId;
    }
}
