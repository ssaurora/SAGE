package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class SessionListSummaryProjectionDto {
    @JsonProperty("total_sessions")
    private int totalSessions;

    @JsonProperty("needs_action_count")
    private int needsActionCount;

    @JsonProperty("running_count")
    private int runningCount;

    @JsonProperty("ready_results_count")
    private int readyResultsCount;

    @JsonProperty("priority_sessions")
    private final List<SessionListItemDto> prioritySessions = new ArrayList<>();

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

    public List<SessionListItemDto> getPrioritySessions() {
        return prioritySessions;
    }
}
