package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class ResumeTaskRequest {
    @JsonProperty("resume_request_id")
    private String resumeRequestId;

    @JsonProperty("attachment_ids")
    private List<String> attachmentIds;

    @JsonProperty("slot_overrides")
    private Map<String, Object> slotOverrides;

    @JsonProperty("args_overrides")
    private Map<String, Object> argsOverrides;

    @JsonProperty("user_note")
    private String userNote;

    public String getResumeRequestId() {
        return resumeRequestId;
    }

    public void setResumeRequestId(String resumeRequestId) {
        this.resumeRequestId = resumeRequestId;
    }

    public List<String> getAttachmentIds() {
        return attachmentIds;
    }

    public void setAttachmentIds(List<String> attachmentIds) {
        this.attachmentIds = attachmentIds;
    }

    public Map<String, Object> getSlotOverrides() {
        return slotOverrides;
    }

    public void setSlotOverrides(Map<String, Object> slotOverrides) {
        this.slotOverrides = slotOverrides;
    }

    public Map<String, Object> getArgsOverrides() {
        return argsOverrides;
    }

    public void setArgsOverrides(Map<String, Object> argsOverrides) {
        this.argsOverrides = argsOverrides;
    }

    public String getUserNote() {
        return userNote;
    }

    public void setUserNote(String userNote) {
        this.userNote = userNote;
    }
}

