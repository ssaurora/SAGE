package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class SessionStreamResponse {
    private AnalysisSessionResponse session;

    private SessionMessagesResponse messages;

    @JsonProperty("progress_projection")
    private Map<String, Object> progressProjection;

    @JsonProperty("waiting_projection")
    private Map<String, Object> waitingProjection;

    @JsonProperty("latest_result_summary")
    private Map<String, Object> latestResultSummary;

    public AnalysisSessionResponse getSession() {
        return session;
    }

    public void setSession(AnalysisSessionResponse session) {
        this.session = session;
    }

    public SessionMessagesResponse getMessages() {
        return messages;
    }

    public void setMessages(SessionMessagesResponse messages) {
        this.messages = messages;
    }

    public Map<String, Object> getProgressProjection() {
        return progressProjection;
    }

    public void setProgressProjection(Map<String, Object> progressProjection) {
        this.progressProjection = progressProjection;
    }

    public Map<String, Object> getWaitingProjection() {
        return waitingProjection;
    }

    public void setWaitingProjection(Map<String, Object> waitingProjection) {
        this.waitingProjection = waitingProjection;
    }

    public Map<String, Object> getLatestResultSummary() {
        return latestResultSummary;
    }

    public void setLatestResultSummary(Map<String, Object> latestResultSummary) {
        this.latestResultSummary = latestResultSummary;
    }
}
