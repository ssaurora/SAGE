package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class SessionMessageDto {
    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("result_bundle_id")
    private String resultBundleId;

    private String role;

    @JsonProperty("message_type")
    private String messageType;

    private Map<String, Object> content;

    @JsonProperty("attachment_refs")
    private Map<String, Object> attachmentRefs;

    @JsonProperty("action_schema")
    private Map<String, Object> actionSchema;

    @JsonProperty("related_object_refs")
    private Map<String, Object> relatedObjectRefs;

    @JsonProperty("created_at")
    private String createdAt;

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

    public Map<String, Object> getContent() {
        return content;
    }

    public void setContent(Map<String, Object> content) {
        this.content = content;
    }

    public Map<String, Object> getAttachmentRefs() {
        return attachmentRefs;
    }

    public void setAttachmentRefs(Map<String, Object> attachmentRefs) {
        this.attachmentRefs = attachmentRefs;
    }

    public Map<String, Object> getActionSchema() {
        return actionSchema;
    }

    public void setActionSchema(Map<String, Object> actionSchema) {
        this.actionSchema = actionSchema;
    }

    public Map<String, Object> getRelatedObjectRefs() {
        return relatedObjectRefs;
    }

    public void setRelatedObjectRefs(Map<String, Object> relatedObjectRefs) {
        this.relatedObjectRefs = relatedObjectRefs;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
