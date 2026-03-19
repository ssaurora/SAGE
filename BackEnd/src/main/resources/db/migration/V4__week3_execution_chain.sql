CREATE TABLE IF NOT EXISTS job_record (
    job_id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL UNIQUE REFERENCES task_state(task_id),
    job_state VARCHAR(32) NOT NULL,
    execution_graph_json TEXT,
    runtime_assertions_json TEXT,
    planning_pass2_summary_json TEXT,
    result_object_json TEXT,
    error_json TEXT,
    accepted_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    last_heartbeat_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_job_record_state CHECK (job_state IN ('ACCEPTED', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);

ALTER TABLE task_state
    ADD COLUMN IF NOT EXISTS job_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS pass2_result_json TEXT,
    ADD COLUMN IF NOT EXISTS result_object_summary_json TEXT;

ALTER TABLE task_state
    DROP CONSTRAINT IF EXISTS uq_task_state_job_id;

ALTER TABLE task_state
    ADD CONSTRAINT uq_task_state_job_id UNIQUE (job_id);

CREATE INDEX IF NOT EXISTS idx_job_record_state_updated_at ON job_record (job_state, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_job_record_task_id ON job_record (task_id);
CREATE INDEX IF NOT EXISTS idx_job_record_last_heartbeat_at ON job_record (last_heartbeat_at DESC);
