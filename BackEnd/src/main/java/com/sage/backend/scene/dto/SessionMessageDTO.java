package com.sage.backend.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class SessionMessageDTO {
    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("session_id")
    private String sessionId;

    private String role;

    @JsonProperty("message_type")
    private String messageType;

    private String surface;

    private String stage;

    private Map<String, Object> content;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("related_task_id")
    private String relatedTaskId;

    @JsonProperty("related_result_bundle_id")
    private String relatedResultBundleId;

    @JsonProperty("related_waiting_reason_type")
    private String relatedWaitingReasonType;

    @JsonProperty("primary_explanation")
    private Boolean primaryExplanation;

    @JsonProperty("developer_trace")
    private Map<String, Object> developerTrace;

    @JsonProperty("attachment_refs")
    private Map<String, Object> attachmentRefs;

    @JsonProperty("action_schema")
    private Map<String, Object> actionSchema;

    @JsonProperty("related_object_refs")
    private Map<String, Object> relatedObjectRefs;

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

    public String getSurface() {
        return surface;
    }

    public void setSurface(String surface) {
        this.surface = surface;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public void setContent(Map<String, Object> content) {
        this.content = content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
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

    public String getRelatedWaitingReasonType() {
        return relatedWaitingReasonType;
    }

    public void setRelatedWaitingReasonType(String relatedWaitingReasonType) {
        this.relatedWaitingReasonType = relatedWaitingReasonType;
    }

    public Boolean getPrimaryExplanation() {
        return primaryExplanation;
    }

    public void setPrimaryExplanation(Boolean primaryExplanation) {
        this.primaryExplanation = primaryExplanation;
    }

    public Map<String, Object> getDeveloperTrace() {
        return developerTrace;
    }

    public void setDeveloperTrace(Map<String, Object> developerTrace) {
        this.developerTrace = developerTrace;
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
}
