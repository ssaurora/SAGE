package com.sage.backend.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RunSurfaceProjectionDTO {

    private Boolean visible;

    private String phase;

    private String label;

    private String detail;

    private Boolean completed;

    @JsonProperty("authority_note")
    private String authorityNote;

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public String getAuthorityNote() {
        return authorityNote;
    }

    public void setAuthorityNote(String authorityNote) {
        this.authorityNote = authorityNote;
    }
}
