package com.sage.backend.model;

public enum TaskStatus {
    CREATED,
    COGNIZING,
    VALIDATING,
    WAITING_USER,
    PLANNING,
    QUEUED,
    RUNNING,
    RESULT_PROCESSING,
    FAILED,
    SUCCEEDED,
    CANCELLED
}
