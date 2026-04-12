package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UploadSessionAttachmentResponse {
    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("attachment_id")
    private String attachmentId;

    @JsonProperty("logical_slot")
    private String logicalSlot;

    @JsonProperty("stored_path")
    private String storedPath;

    @JsonProperty("size_bytes")
    private Long sizeBytes;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("assignment_status")
    private String assignmentStatus;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getLogicalSlot() {
        return logicalSlot;
    }

    public void setLogicalSlot(String logicalSlot) {
        this.logicalSlot = logicalSlot;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getAssignmentStatus() {
        return assignmentStatus;
    }

    public void setAssignmentStatus(String assignmentStatus) {
        this.assignmentStatus = assignmentStatus;
    }
}
