package com.sage.backend.session.dto;

import java.util.ArrayList;
import java.util.List;

public class SessionListResponse {
    private final List<SessionListItemDto> items = new ArrayList<>();

    private SessionListSummaryProjectionDto summary;

    public List<SessionListItemDto> getItems() {
        return items;
    }

    public SessionListSummaryProjectionDto getSummary() {
        return summary;
    }

    public void setSummary(SessionListSummaryProjectionDto summary) {
        this.summary = summary;
    }
}
