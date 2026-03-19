package com.sage.backend.model;

import java.time.OffsetDateTime;

public class RepairRecord {
    private Long id;
    private String taskId;
    private Integer attemptNo;
    private String resumeRequestId;
    private String dispatcherOutputJson;
    private String repairProposalJson;
    private String resumePayloadJson;
    private String result;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
    }

    public String getResumeRequestId() {
        return resumeRequestId;
    }

    public void setResumeRequestId(String resumeRequestId) {
        this.resumeRequestId = resumeRequestId;
    }

    public String getDispatcherOutputJson() {
        return dispatcherOutputJson;
    }

    public void setDispatcherOutputJson(String dispatcherOutputJson) {
        this.dispatcherOutputJson = dispatcherOutputJson;
    }

    public String getRepairProposalJson() {
        return repairProposalJson;
    }

    public void setRepairProposalJson(String repairProposalJson) {
        this.repairProposalJson = repairProposalJson;
    }

    public String getResumePayloadJson() {
        return resumePayloadJson;
    }

    public void setResumePayloadJson(String resumePayloadJson) {
        this.resumePayloadJson = resumePayloadJson;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

