package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ForceRevertCheckpointRequest {
    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("target_checkpoint_version")
    private Integer targetCheckpointVersion;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Integer getTargetCheckpointVersion() {
        return targetCheckpointVersion;
    }

    public void setTargetCheckpointVersion(Integer targetCheckpointVersion) {
        this.targetCheckpointVersion = targetCheckpointVersion;
    }
}
