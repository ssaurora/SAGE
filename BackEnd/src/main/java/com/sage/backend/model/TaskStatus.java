package com.sage.backend.model;

public enum TaskStatus {
    CREATED,
    COGNIZING,
    VALIDATING,
    WAITING_USER,
    RESUMING,
    PLANNING,
    QUEUED,
    RUNNING,
    RESULT_PROCESSING,
    ARTIFACT_PROMOTING,
    FAILED,
    STATE_CORRUPTED,
    SUCCEEDED,
    CANCELLED
}
