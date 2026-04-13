package com.sage.backend.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PostSceneSessionMessageResponse {
    @JsonProperty("accepted_message")
    private SessionMessageDTO acceptedMessage;

    private SessionProjectionDTO session;

    public SessionMessageDTO getAcceptedMessage() {
        return acceptedMessage;
    }

    public void setAcceptedMessage(SessionMessageDTO acceptedMessage) {
        this.acceptedMessage = acceptedMessage;
    }

    public SessionProjectionDTO getSession() {
        return session;
    }

    public void setSession(SessionProjectionDTO session) {
        this.session = session;
    }
}
