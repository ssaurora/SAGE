package com.sage.backend.cognition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class CognitionPassBRequest {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("user_query")
    private String userQuery;

    @JsonProperty("state_version")
    private Integer stateVersion;

    @JsonProperty("pass1_result")
    private JsonNode pass1Result;

    @JsonProperty("goal_parse")
    private JsonNode goalParse;

    @JsonProperty("skill_route")
    private JsonNode skillRoute;

    @JsonProperty("user_note")
    private String userNote;

    @JsonProperty("attachment_facts")
    private JsonNode attachmentFacts;

    @JsonProperty("accepted_overrides")
    private JsonNode acceptedOverrides;

    @JsonProperty("resume_context")
    private JsonNode resumeContext;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }

    public Integer getStateVersion() {
        return stateVersion;
    }

    public void setStateVersion(Integer stateVersion) {
        this.stateVersion = stateVersion;
    }

    public JsonNode getPass1Result() {
        return pass1Result;
    }

    public void setPass1Result(JsonNode pass1Result) {
        this.pass1Result = pass1Result;
    }

    public JsonNode getGoalParse() {
        return goalParse;
    }

    public void setGoalParse(JsonNode goalParse) {
        this.goalParse = goalParse;
    }

    public JsonNode getSkillRoute() {
        return skillRoute;
    }

    public void setSkillRoute(JsonNode skillRoute) {
        this.skillRoute = skillRoute;
    }

    public String getUserNote() {
        return userNote;
    }

    public void setUserNote(String userNote) {
        this.userNote = userNote;
    }

    public JsonNode getAttachmentFacts() {
        return attachmentFacts == null ? JsonNodeFactory.instance.arrayNode() : attachmentFacts;
    }

    public void setAttachmentFacts(JsonNode attachmentFacts) {
        this.attachmentFacts = attachmentFacts;
    }

    public JsonNode getAcceptedOverrides() {
        return acceptedOverrides == null ? JsonNodeFactory.instance.objectNode() : acceptedOverrides;
    }

    public void setAcceptedOverrides(JsonNode acceptedOverrides) {
        this.acceptedOverrides = acceptedOverrides;
    }

    public JsonNode getResumeContext() {
        return resumeContext == null ? JsonNodeFactory.instance.objectNode() : resumeContext;
    }

    public void setResumeContext(JsonNode resumeContext) {
        this.resumeContext = resumeContext;
    }
}
