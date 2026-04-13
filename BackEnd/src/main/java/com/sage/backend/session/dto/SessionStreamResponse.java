package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SessionStreamResponse {
    private AnalysisSessionResponse session;

    private SessionMessagesResponse messages;

    @JsonProperty("progress_projection")
    private SessionProgressProjectionDto progressProjection;

    @JsonProperty("waiting_projection")
    private WaitingForUserProjectionDto waitingProjection;

    @JsonProperty("latest_result_summary")
    private ResultConversationProjectionDto latestResultSummary;

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

    public SessionProgressProjectionDto getProgressProjection() {
        return progressProjection;
    }

    public void setProgressProjection(SessionProgressProjectionDto progressProjection) {
        this.progressProjection = progressProjection;
    }

    public WaitingForUserProjectionDto getWaitingProjection() {
        return waitingProjection;
    }

    public void setWaitingProjection(WaitingForUserProjectionDto waitingProjection) {
        this.waitingProjection = waitingProjection;
    }

    public ResultConversationProjectionDto getLatestResultSummary() {
        return latestResultSummary;
    }

    public void setLatestResultSummary(ResultConversationProjectionDto latestResultSummary) {
        this.latestResultSummary = latestResultSummary;
    }
}
