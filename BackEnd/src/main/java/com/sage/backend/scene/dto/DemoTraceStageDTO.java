package com.sage.backend.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DemoTraceStageDTO {

    @JsonProperty("stage_id")
    private String stageId;

    @JsonProperty("stage_label")
    private String stageLabel;

    private String plane;

    private String status;

    @JsonProperty("trace_authority_level")
    private String traceAuthorityLevel;

    @JsonProperty("projected_stage_surface")
    private Boolean projectedStageSurface;

    @JsonProperty("stage_surface_note")
    private String stageSurfaceNote;

    @JsonProperty("started_at")
    private String startedAt;

    @JsonProperty("completed_at")
    private String completedAt;

    private String summary;

    @JsonProperty("key_outputs")
    private final List<String> keyOutputs = new ArrayList<>();

    private Map<String, Object> payload;

    private final List<DemoTraceStageDTO> children = new ArrayList<>();

    public String getStageId() {
        return stageId;
    }

    public void setStageId(String stageId) {
        this.stageId = stageId;
    }

    public String getStageLabel() {
        return stageLabel;
    }

    public void setStageLabel(String stageLabel) {
        this.stageLabel = stageLabel;
    }

    public String getPlane() {
        return plane;
    }

    public void setPlane(String plane) {
        this.plane = plane;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTraceAuthorityLevel() {
        return traceAuthorityLevel;
    }

    public void setTraceAuthorityLevel(String traceAuthorityLevel) {
        this.traceAuthorityLevel = traceAuthorityLevel;
    }

    public Boolean getProjectedStageSurface() {
        return projectedStageSurface;
    }

    public void setProjectedStageSurface(Boolean projectedStageSurface) {
        this.projectedStageSurface = projectedStageSurface;
    }

    public String getStageSurfaceNote() {
        return stageSurfaceNote;
    }

    public void setStageSurfaceNote(String stageSurfaceNote) {
        this.stageSurfaceNote = stageSurfaceNote;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getKeyOutputs() {
        return keyOutputs;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public List<DemoTraceStageDTO> getChildren() {
        return children;
    }
}
