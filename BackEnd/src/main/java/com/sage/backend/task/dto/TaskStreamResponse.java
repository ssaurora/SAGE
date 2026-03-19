package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskStreamResponse {
    private TaskDetailResponse task;

    @JsonProperty("events")
    private TaskEventsResponse eventsResponse;

    public TaskDetailResponse getTask() {
        return task;
    }

    public void setTask(TaskDetailResponse task) {
        this.task = task;
    }

    public TaskEventsResponse getEventsResponse() {
        return eventsResponse;
    }

    public void setEventsResponse(TaskEventsResponse eventsResponse) {
        this.eventsResponse = eventsResponse;
    }
}

