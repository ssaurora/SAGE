package com.sage.backend.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SceneListSummaryDTO {
    @JsonProperty("total_scenes")
    private Integer totalScenes;

    @JsonProperty("needs_action_count")
    private Integer needsActionCount;

    @JsonProperty("running_count")
    private Integer runningCount;

    @JsonProperty("ready_results_count")
    private Integer readyResultsCount;

    public Integer getTotalScenes() {
        return totalScenes;
    }

    public void setTotalScenes(Integer totalScenes) {
        this.totalScenes = totalScenes;
    }

    public Integer getNeedsActionCount() {
        return needsActionCount;
    }

    public void setNeedsActionCount(Integer needsActionCount) {
        this.needsActionCount = needsActionCount;
    }

    public Integer getRunningCount() {
        return runningCount;
    }

    public void setRunningCount(Integer runningCount) {
        this.runningCount = runningCount;
    }

    public Integer getReadyResultsCount() {
        return readyResultsCount;
    }

    public void setReadyResultsCount(Integer readyResultsCount) {
        this.readyResultsCount = readyResultsCount;
    }
}
