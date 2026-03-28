package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CorruptionStateView {
    @JsonProperty("is_corrupted")
    private Boolean corrupted;

    private String reason;

    @JsonProperty("corrupted_since")
    private String corruptedSince;

    public Boolean getCorrupted() {
        return corrupted;
    }

    public void setCorrupted(Boolean corrupted) {
        this.corrupted = corrupted;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCorruptedSince() {
        return corruptedSince;
    }

    public void setCorruptedSince(String corruptedSince) {
        this.corruptedSince = corruptedSince;
    }
}
