package com.sage.backend.execution.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public class JobStatusResponse {
    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("job_state")
    private String jobState;

    @JsonProperty("last_heartbeat_at")
    private OffsetDateTime lastHeartbeatAt;

    @JsonProperty("started_at")
    private OffsetDateTime startedAt;

    @JsonProperty("finished_at")
    private OffsetDateTime finishedAt;

    @JsonProperty("result_object")
    private JsonNode resultObject;

    @JsonProperty("result_bundle")
    private JsonNode resultBundle;

    @JsonProperty("final_explanation")
    private JsonNode finalExplanation;

    @JsonProperty("failure_summary")
    private JsonNode failureSummary;

    @JsonProperty("docker_runtime_evidence")
    private JsonNode dockerRuntimeEvidence;

    @JsonProperty("error_object")
    private JsonNode errorObject;

    @JsonProperty("cancel_requested_at")
    private OffsetDateTime cancelRequestedAt;

    @JsonProperty("cancelled_at")
    private OffsetDateTime cancelledAt;

    @JsonProperty("cancel_reason")
    private String cancelReason;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getJobState() {
        return jobState;
    }

    public void setJobState(String jobState) {
        this.jobState = jobState;
    }

    public OffsetDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(OffsetDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public JsonNode getResultObject() {
        return resultObject;
    }

    public void setResultObject(JsonNode resultObject) {
        this.resultObject = resultObject;
    }

    public JsonNode getErrorObject() {
        return errorObject;
    }

    public void setErrorObject(JsonNode errorObject) {
        this.errorObject = errorObject;
    }

    public JsonNode getResultBundle() {
        return resultBundle;
    }

    public void setResultBundle(JsonNode resultBundle) {
        this.resultBundle = resultBundle;
    }

    public JsonNode getFinalExplanation() {
        return finalExplanation;
    }

    public void setFinalExplanation(JsonNode finalExplanation) {
        this.finalExplanation = finalExplanation;
    }

    public JsonNode getFailureSummary() {
        return failureSummary;
    }

    public void setFailureSummary(JsonNode failureSummary) {
        this.failureSummary = failureSummary;
    }

    public JsonNode getDockerRuntimeEvidence() {
        return dockerRuntimeEvidence;
    }

    public void setDockerRuntimeEvidence(JsonNode dockerRuntimeEvidence) {
        this.dockerRuntimeEvidence = dockerRuntimeEvidence;
    }

    public OffsetDateTime getCancelRequestedAt() {
        return cancelRequestedAt;
    }

    public void setCancelRequestedAt(OffsetDateTime cancelRequestedAt) {
        this.cancelRequestedAt = cancelRequestedAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(OffsetDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
}
