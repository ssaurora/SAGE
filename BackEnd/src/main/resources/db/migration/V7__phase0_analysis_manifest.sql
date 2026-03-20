CREATE TABLE IF NOT EXISTS analysis_manifest (
    manifest_id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL REFERENCES task_state(task_id),
    attempt_no INTEGER NOT NULL,
    manifest_version INTEGER NOT NULL,
    goal_parse_json TEXT,
    skill_route_json TEXT,
    logical_input_roles_json TEXT,
    slot_schema_view_json TEXT,
    slot_bindings_json TEXT,
    args_draft_json TEXT,
    validation_summary_json TEXT,
    execution_graph_json TEXT,
    runtime_assertions_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_analysis_manifest_task_attempt_version UNIQUE (task_id, attempt_no, manifest_version)
);

ALTER TABLE task_state
    ADD COLUMN IF NOT EXISTS active_manifest_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS active_manifest_version INTEGER;

CREATE INDEX IF NOT EXISTS idx_analysis_manifest_task_attempt_created_at
    ON analysis_manifest (task_id, attempt_no, created_at DESC);
