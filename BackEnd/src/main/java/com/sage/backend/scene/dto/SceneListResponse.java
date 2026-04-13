package com.sage.backend.scene.dto;

import java.util.ArrayList;
import java.util.List;

public class SceneListResponse {
    private final List<SceneSummaryDTO> items = new ArrayList<>();

    private SceneListSummaryDTO summary;

    public List<SceneSummaryDTO> getItems() {
        return items;
    }

    public SceneListSummaryDTO getSummary() {
        return summary;
    }

    public void setSummary(SceneListSummaryDTO summary) {
        this.summary = summary;
    }
}
