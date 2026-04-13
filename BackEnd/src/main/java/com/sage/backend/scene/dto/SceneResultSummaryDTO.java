package com.sage.backend.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SceneResultSummaryDTO {
    @JsonProperty("latest_result_bundle_id")
    private String latestResultBundleId;

    @JsonProperty("ready_result_count")
    private Integer readyResultCount;

    @JsonProperty("has_ready_result")
    private Boolean hasReadyResult;

    @JsonProperty("latest_result_created_at")
    private String latestResultCreatedAt;

    @JsonProperty("final_explanation_available")
    private Boolean finalExplanationAvailable;

    @JsonProperty("result_summary_text")
    private String resultSummaryText;

    public String getLatestResultBundleId() {
        return latestResultBundleId;
    }

    public void setLatestResultBundleId(String latestResultBundleId) {
        this.latestResultBundleId = latestResultBundleId;
    }

    public Integer getReadyResultCount() {
        return readyResultCount;
    }

    public void setReadyResultCount(Integer readyResultCount) {
        this.readyResultCount = readyResultCount;
    }

    public Boolean getHasReadyResult() {
        return hasReadyResult;
    }

    public void setHasReadyResult(Boolean hasReadyResult) {
        this.hasReadyResult = hasReadyResult;
    }

    public String getLatestResultCreatedAt() {
        return latestResultCreatedAt;
    }

    public void setLatestResultCreatedAt(String latestResultCreatedAt) {
        this.latestResultCreatedAt = latestResultCreatedAt;
    }

    public Boolean getFinalExplanationAvailable() {
        return finalExplanationAvailable;
    }

    public void setFinalExplanationAvailable(Boolean finalExplanationAvailable) {
        this.finalExplanationAvailable = finalExplanationAvailable;
    }

    public String getResultSummaryText() {
        return resultSummaryText;
    }

    public void setResultSummaryText(String resultSummaryText) {
        this.resultSummaryText = resultSummaryText;
    }
}
