package com.sage.backend.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class SessionMessagesResponseDTO {
    @JsonProperty("session_id")
    private String sessionId;

    private final List<SessionMessageDTO> items = new ArrayList<>();

    @JsonProperty("next_cursor")
    private String nextCursor;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<SessionMessageDTO> getItems() {
        return items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}
