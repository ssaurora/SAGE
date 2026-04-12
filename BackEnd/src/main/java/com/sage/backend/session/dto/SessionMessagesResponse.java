package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class SessionMessagesResponse {
    @JsonProperty("session_id")
    private String sessionId;

    private final List<SessionMessageDto> items = new ArrayList<>();

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<SessionMessageDto> getItems() {
        return items;
    }
}
