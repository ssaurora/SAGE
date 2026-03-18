package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class CreateTaskRequest {
    @NotBlank
    @JsonProperty("user_query")
    private String userQuery;

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }
}

