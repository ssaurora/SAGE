CREATE TABLE IF NOT EXISTS task_state (
    task_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    current_state VARCHAR(32) NOT NULL,
    state_version INTEGER NOT NULL CHECK (state_version >= 0),
    user_query TEXT NOT NULL,
    pass1_result_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS event_log (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL REFERENCES task_state(task_id),
    event_type VARCHAR(64) NOT NULL,
    from_state VARCHAR(32),
    to_state VARCHAR(32),
    state_version INTEGER NOT NULL,
    event_payload_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS audit_record (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL REFERENCES task_state(task_id),
    action_type VARCHAR(64) NOT NULL,
    action_result VARCHAR(32) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    detail_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_event_log_task_created_at ON event_log (task_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_event_log_task_state_version ON event_log (task_id, state_version);
CREATE INDEX IF NOT EXISTS idx_audit_record_task_created_at ON audit_record (task_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_record_trace_id ON audit_record (trace_id);

