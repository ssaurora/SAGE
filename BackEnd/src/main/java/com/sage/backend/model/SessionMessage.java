package com.sage.backend.model;

import java.time.OffsetDateTime;

public class SessionMessage {
    private String messageId;
    private String sessionId;
    private String taskId;
    private String resultBundleId;
    private String role;
    private String messageType;
    private String contentJson;
    private String attachmentRefsJson;
    private String actionSchemaJson;
    private String relatedObjectRefsJson;
    private OffsetDateTime createdAt;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

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

    public String getResultBundleId() {
        return resultBundleId;
    }

    public void setResultBundleId(String resultBundleId) {
        this.resultBundleId = resultBundleId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getContentJson() {
        return contentJson;
    }

    public void setContentJson(String contentJson) {
        this.contentJson = contentJson;
    }

    public String getAttachmentRefsJson() {
        return attachmentRefsJson;
    }

    public void setAttachmentRefsJson(String attachmentRefsJson) {
        this.attachmentRefsJson = attachmentRefsJson;
    }

    public String getActionSchemaJson() {
        return actionSchemaJson;
    }

    public void setActionSchemaJson(String actionSchemaJson) {
        this.actionSchemaJson = actionSchemaJson;
    }

    public String getRelatedObjectRefsJson() {
        return relatedObjectRefsJson;
    }

    public void setRelatedObjectRefsJson(String relatedObjectRefsJson) {
        this.relatedObjectRefsJson = relatedObjectRefsJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
