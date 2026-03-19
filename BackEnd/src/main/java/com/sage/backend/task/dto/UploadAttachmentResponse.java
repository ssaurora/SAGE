package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UploadAttachmentResponse {
    @JsonProperty("attachment_id")
    private String attachmentId;

    @JsonProperty("task_id")
    private String taskId;

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

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
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

