package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SessionListResponse {
    private final List<SessionListItem> items = new ArrayList<>();

    private SessionListSummaryProjection summary;

    public List<SessionListItem> getItems() {
        return items;
    }

    public SessionListSummaryProjection getSummary() {
        return summary;
    }

    public void setSummary(SessionListSummaryProjection summary) {
        this.summary = summary;
    }

    public static class SessionListItem {
        @JsonProperty("session_id")
        private String sessionId;

        private String title;

        @JsonProperty("user_goal")
        private String userGoal;

        private String status;

        @JsonProperty("scene_id")
        private String sceneId;

        @JsonProperty("current_task_id")
        private String currentTaskId;

        @JsonProperty("latest_result_bundle_id")
        private String latestResultBundleId;

        @JsonProperty("session_summary")
        private Map<String, Object> sessionSummary;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("updated_at")
        private String updatedAt;

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getUserGoal() {
            return userGoal;
        }

        public void setUserGoal(String userGoal) {
            this.userGoal = userGoal;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getSceneId() {
            return sceneId;
        }

        public void setSceneId(String sceneId) {
            this.sceneId = sceneId;
        }

        public String getCurrentTaskId() {
            return currentTaskId;
        }

        public void setCurrentTaskId(String currentTaskId) {
            this.currentTaskId = currentTaskId;
        }

        public String getLatestResultBundleId() {
            return latestResultBundleId;
        }

        public void setLatestResultBundleId(String latestResultBundleId) {
            this.latestResultBundleId = latestResultBundleId;
        }

        public Map<String, Object> getSessionSummary() {
            return sessionSummary;
        }

        public void setSessionSummary(Map<String, Object> sessionSummary) {
            this.sessionSummary = sessionSummary;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class SessionListSummaryProjection {
        @JsonProperty("total_sessions")
        private int totalSessions;

        @JsonProperty("needs_action_count")
        private int needsActionCount;

        @JsonProperty("running_count")
        private int runningCount;

        @JsonProperty("ready_results_count")
        private int readyResultsCount;

        @JsonProperty("priority_sessions")
        private final List<SessionListItem> prioritySessions = new ArrayList<>();

        public int getTotalSessions() {
            return totalSessions;
        }

        public void setTotalSessions(int totalSessions) {
            this.totalSessions = totalSessions;
        }

        public int getNeedsActionCount() {
            return needsActionCount;
        }

        public void setNeedsActionCount(int needsActionCount) {
            this.needsActionCount = needsActionCount;
        }

        public int getRunningCount() {
            return runningCount;
        }

        public void setRunningCount(int runningCount) {
            this.runningCount = runningCount;
        }

        public int getReadyResultsCount() {
            return readyResultsCount;
        }

        public void setReadyResultsCount(int readyResultsCount) {
            this.readyResultsCount = readyResultsCount;
        }

        public List<SessionListItem> getPrioritySessions() {
            return prioritySessions;
        }
    }
}
