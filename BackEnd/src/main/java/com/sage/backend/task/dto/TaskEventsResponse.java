package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class TaskEventsResponse {
    private List<EventItem> items = new ArrayList<>();

    public List<EventItem> getItems() {
        return items;
    }

    public void setItems(List<EventItem> items) {
        this.items = items;
    }

    public static class EventItem {
        @JsonProperty("event_type")
        private String eventType;

        @JsonProperty("from_state")
        private String fromState;

        @JsonProperty("to_state")
        private String toState;

        @JsonProperty("state_version")
        private Integer stateVersion;

        @JsonProperty("created_at")
        private String createdAt;

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getFromState() {
            return fromState;
        }

        public void setFromState(String fromState) {
            this.fromState = fromState;
        }

        public String getToState() {
            return toState;
        }

        public void setToState(String toState) {
            this.toState = toState;
        }

        public Integer getStateVersion() {
            return stateVersion;
        }

        public void setStateVersion(Integer stateVersion) {
            this.stateVersion = stateVersion;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }
}

