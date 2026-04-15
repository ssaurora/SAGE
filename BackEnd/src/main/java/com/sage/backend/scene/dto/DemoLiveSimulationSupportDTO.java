package com.sage.backend.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class DemoLiveSimulationSupportDTO {

    @JsonProperty("demo_narrative_type")
    private String demoNarrativeType;

    @JsonProperty("demo_run_scope")
    private String demoRunScope;

    @JsonProperty("demo_run_active")
    private Boolean demoRunActive;

    @JsonProperty("demo_run_id")
    private String demoRunId;

    @JsonProperty("demo_current_step")
    private String demoCurrentStep;

    @JsonProperty("demo_completed_steps")
    private final List<String> demoCompletedSteps = new ArrayList<>();

    @JsonProperty("demo_next_scheduled_step_at")
    private String demoNextScheduledStepAt;

    @JsonProperty("demo_started_at")
    private String demoStartedAt;

    @JsonProperty("demo_last_step_emitted_at")
    private String demoLastStepEmittedAt;

    @JsonProperty("demo_reset_required")
    private Boolean demoResetRequired;

    @JsonProperty("run_surface_projection")
    private RunSurfaceProjectionDTO runSurfaceProjection;

    private final List<DemoTraceStageDTO> stages = new ArrayList<>();

    public String getDemoNarrativeType() {
        return demoNarrativeType;
    }

    public void setDemoNarrativeType(String demoNarrativeType) {
        this.demoNarrativeType = demoNarrativeType;
    }

    public String getDemoRunScope() {
        return demoRunScope;
    }

    public void setDemoRunScope(String demoRunScope) {
        this.demoRunScope = demoRunScope;
    }

    public Boolean getDemoRunActive() {
        return demoRunActive;
    }

    public void setDemoRunActive(Boolean demoRunActive) {
        this.demoRunActive = demoRunActive;
    }

    public String getDemoRunId() {
        return demoRunId;
    }

    public void setDemoRunId(String demoRunId) {
        this.demoRunId = demoRunId;
    }

    public String getDemoCurrentStep() {
        return demoCurrentStep;
    }

    public void setDemoCurrentStep(String demoCurrentStep) {
        this.demoCurrentStep = demoCurrentStep;
    }

    public List<String> getDemoCompletedSteps() {
        return demoCompletedSteps;
    }

    public String getDemoNextScheduledStepAt() {
        return demoNextScheduledStepAt;
    }

    public void setDemoNextScheduledStepAt(String demoNextScheduledStepAt) {
        this.demoNextScheduledStepAt = demoNextScheduledStepAt;
    }

    public String getDemoStartedAt() {
        return demoStartedAt;
    }

    public void setDemoStartedAt(String demoStartedAt) {
        this.demoStartedAt = demoStartedAt;
    }

    public String getDemoLastStepEmittedAt() {
        return demoLastStepEmittedAt;
    }

    public void setDemoLastStepEmittedAt(String demoLastStepEmittedAt) {
        this.demoLastStepEmittedAt = demoLastStepEmittedAt;
    }

    public Boolean getDemoResetRequired() {
        return demoResetRequired;
    }

    public void setDemoResetRequired(Boolean demoResetRequired) {
        this.demoResetRequired = demoResetRequired;
    }

    public RunSurfaceProjectionDTO getRunSurfaceProjection() {
        return runSurfaceProjection;
    }

    public void setRunSurfaceProjection(RunSurfaceProjectionDTO runSurfaceProjection) {
        this.runSurfaceProjection = runSurfaceProjection;
    }

    public List<DemoTraceStageDTO> getStages() {
        return stages;
    }
}
