ALTER TABLE task_state
    ADD COLUMN IF NOT EXISTS waiting_context_json TEXT,
    ADD COLUMN IF NOT EXISTS waiting_reason_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS resume_payload_json TEXT,
    ADD COLUMN IF NOT EXISTS resume_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS active_attempt_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS waiting_since TIMESTAMPTZ;

ALTER TABLE job_record
    ADD COLUMN IF NOT EXISTS attempt_no INTEGER NOT NULL DEFAULT 1;

ALTER TABLE job_record
    DROP CONSTRAINT IF EXISTS job_record_task_id_key;

ALTER TABLE job_record
    DROP CONSTRAINT IF EXISTS uq_job_record_task_attempt;

ALTER TABLE job_record
    ADD CONSTRAINT uq_job_record_task_attempt UNIQUE (task_id, attempt_no);

CREATE TABLE IF NOT EXISTS task_attachment (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL REFERENCES task_state(task_id),
    attempt_no INTEGER,
    logical_slot VARCHAR(128),
    assignment_status VARCHAR(16) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    stored_path TEXT NOT NULL,
    checksum VARCHAR(128),
    uploaded_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_task_attachment_assignment_status CHECK (assignment_status IN ('ASSIGNED', 'UNASSIGNED'))
);

CREATE TABLE IF NOT EXISTS repair_record (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL REFERENCES task_state(task_id),
    attempt_no INTEGER NOT NULL,
    resume_request_id VARCHAR(64),
    dispatcher_output_json TEXT,
    repair_proposal_json TEXT,
    resume_payload_json TEXT,
    result VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_repair_record_result CHECK (result IN ('ACCEPTED', 'REJECTED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS task_attempt (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL REFERENCES task_state(task_id),
    attempt_no INTEGER NOT NULL,
    trigger VARCHAR(16) NOT NULL,
    job_id VARCHAR(64),
    status_snapshot_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    CONSTRAINT uq_task_attempt_task_no UNIQUE (task_id, attempt_no),
    CONSTRAINT chk_task_attempt_trigger CHECK (trigger IN ('CREATE', 'RESUME'))
);

CREATE INDEX IF NOT EXISTS idx_task_attachment_task_created_at ON task_attachment (task_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_attachment_task_slot ON task_attachment (task_id, logical_slot);
CREATE UNIQUE INDEX IF NOT EXISTS uq_repair_record_task_resume_request
    ON repair_record (task_id, resume_request_id)
    WHERE resume_request_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_repair_record_task_attempt ON repair_record (task_id, attempt_no DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_attempt_task_no ON task_attempt (task_id, attempt_no DESC);

