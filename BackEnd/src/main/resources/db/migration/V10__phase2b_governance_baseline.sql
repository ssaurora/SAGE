ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'USER';

UPDATE app_user
SET role = 'ADMIN'
WHERE username = 'demo'
  AND role <> 'ADMIN';

ALTER TABLE task_state
    ADD COLUMN IF NOT EXISTS planning_revision INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS checkpoint_version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS inventory_version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS resume_txn_json TEXT,
    ADD COLUMN IF NOT EXISTS corruption_reason TEXT,
    ADD COLUMN IF NOT EXISTS corrupted_since TIMESTAMPTZ;

ALTER TABLE analysis_manifest
    ADD COLUMN IF NOT EXISTS freeze_status VARCHAR(32) NOT NULL DEFAULT 'FROZEN',
    ADD COLUMN IF NOT EXISTS planning_revision INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS checkpoint_version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS graph_digest VARCHAR(128),
    ADD COLUMN IF NOT EXISTS planning_summary_json TEXT,
    ADD COLUMN IF NOT EXISTS capability_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS selected_template VARCHAR(128),
    ADD COLUMN IF NOT EXISTS template_version VARCHAR(64);

UPDATE analysis_manifest
SET freeze_status = 'FROZEN'
WHERE freeze_status IS NULL OR freeze_status = '';

ALTER TABLE analysis_manifest
    DROP CONSTRAINT IF EXISTS chk_analysis_manifest_freeze_status;

ALTER TABLE analysis_manifest
    ADD CONSTRAINT chk_analysis_manifest_freeze_status
        CHECK (freeze_status IN ('CANDIDATE', 'FROZEN'));

CREATE INDEX IF NOT EXISTS idx_analysis_manifest_task_attempt_freeze
    ON analysis_manifest (task_id, attempt_no, freeze_status, created_at DESC);
