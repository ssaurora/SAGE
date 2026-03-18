package com.sage.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MeResponse {
    @JsonProperty("user_id")
    private String userId;

    private String username;

    public MeResponse() {
    }

    public MeResponse(String userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}

