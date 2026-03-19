ALTER TABLE job_record
    DROP CONSTRAINT IF EXISTS chk_job_record_state;

ALTER TABLE job_record
    ADD CONSTRAINT chk_job_record_state
    CHECK (job_state IN ('ACCEPTED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'));

ALTER TABLE job_record
    ADD COLUMN IF NOT EXISTS result_bundle_json TEXT,
    ADD COLUMN IF NOT EXISTS final_explanation_json TEXT,
    ADD COLUMN IF NOT EXISTS failure_summary_json TEXT,
    ADD COLUMN IF NOT EXISTS docker_runtime_evidence_json TEXT,
    ADD COLUMN IF NOT EXISTS cancel_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(128);

ALTER TABLE task_state
    ADD COLUMN IF NOT EXISTS result_bundle_summary_json TEXT,
    ADD COLUMN IF NOT EXISTS final_explanation_summary_json TEXT,
    ADD COLUMN IF NOT EXISTS last_failure_summary_json TEXT;

CREATE INDEX IF NOT EXISTS idx_job_record_cancel_requested_at ON job_record (cancel_requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_job_record_cancelled_at ON job_record (cancelled_at DESC);

