ALTER TABLE task_state
    ADD COLUMN IF NOT EXISTS latest_result_bundle_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS latest_workspace_id VARCHAR(64);

ALTER TABLE job_record
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS provider_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS capability_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS runtime_profile VARCHAR(64),
    ADD COLUMN IF NOT EXISTS workspace_summary_json TEXT,
    ADD COLUMN IF NOT EXISTS artifact_catalog_json TEXT;

CREATE TABLE IF NOT EXISTS workspace_registry (
    workspace_id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL REFERENCES task_state(task_id),
    job_id VARCHAR(64) NOT NULL REFERENCES job_record(job_id),
    attempt_no INTEGER NOT NULL,
    runtime_profile VARCHAR(64) NOT NULL,
    container_name VARCHAR(128),
    host_workspace_path TEXT NOT NULL,
    archive_path TEXT,
    workspace_state VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    cleaned_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workspace_registry_task_attempt UNIQUE (task_id, attempt_no),
    CONSTRAINT uq_workspace_registry_job UNIQUE (job_id),
    CONSTRAINT chk_workspace_registry_state CHECK (workspace_state IN ('CREATED', 'RUNNING', 'ARCHIVED', 'CLEANED', 'FAILED_CLEANUP'))
);

CREATE INDEX IF NOT EXISTS idx_workspace_registry_task_attempt ON workspace_registry (task_id, attempt_no DESC);
CREATE INDEX IF NOT EXISTS idx_workspace_registry_job ON workspace_registry (job_id);
CREATE INDEX IF NOT EXISTS idx_workspace_registry_state_updated_at ON workspace_registry (workspace_state, updated_at DESC);

CREATE TABLE IF NOT EXISTS result_bundle_record (
    result_bundle_id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL REFERENCES task_state(task_id),
    job_id VARCHAR(64) NOT NULL REFERENCES job_record(job_id),
    attempt_no INTEGER NOT NULL,
    manifest_id VARCHAR(64),
    workspace_id VARCHAR(64) NOT NULL REFERENCES workspace_registry(workspace_id),
    result_bundle_json TEXT NOT NULL,
    final_explanation_json TEXT,
    summary_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_result_bundle_record_job UNIQUE (job_id)
);

CREATE INDEX IF NOT EXISTS idx_result_bundle_record_task_attempt ON result_bundle_record (task_id, attempt_no DESC);
CREATE INDEX IF NOT EXISTS idx_result_bundle_record_job ON result_bundle_record (job_id);
CREATE INDEX IF NOT EXISTS idx_result_bundle_record_workspace ON result_bundle_record (workspace_id);

CREATE TABLE IF NOT EXISTS artifact_index (
    artifact_id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL REFERENCES task_state(task_id),
    job_id VARCHAR(64) NOT NULL REFERENCES job_record(job_id),
    attempt_no INTEGER NOT NULL,
    workspace_id VARCHAR(64) NOT NULL REFERENCES workspace_registry(workspace_id),
    result_bundle_id VARCHAR(64),
    artifact_role VARCHAR(32) NOT NULL,
    logical_name VARCHAR(128) NOT NULL,
    relative_path TEXT NOT NULL,
    absolute_path TEXT,
    content_type VARCHAR(255),
    size_bytes BIGINT,
    sha256 VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_artifact_role CHECK (artifact_role IN ('PRIMARY_OUTPUT', 'INTERMEDIATE_OUTPUT', 'AUDIT_ARTIFACT', 'DERIVED_OUTPUT', 'LOG'))
);

CREATE INDEX IF NOT EXISTS idx_artifact_index_task_attempt ON artifact_index (task_id, attempt_no DESC);
CREATE INDEX IF NOT EXISTS idx_artifact_index_job ON artifact_index (job_id);
CREATE INDEX IF NOT EXISTS idx_artifact_index_workspace ON artifact_index (workspace_id);
CREATE INDEX IF NOT EXISTS idx_artifact_index_role_created_at ON artifact_index (artifact_role, created_at DESC);

CREATE TABLE IF NOT EXISTS capability_registry (
    capability_key VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    skill_name VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    default_provider_key VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS provider_registry (
    provider_key VARCHAR(64) PRIMARY KEY,
    capability_key VARCHAR(64) NOT NULL REFERENCES capability_registry(capability_key),
    provider_type VARCHAR(64) NOT NULL,
    base_url TEXT NOT NULL,
    runtime_profile VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO capability_registry(capability_key, display_name, skill_name, enabled, default_provider_key)
VALUES ('water_yield', 'Water Yield', 'water_yield', TRUE, 'planning-pass1-local')
ON CONFLICT (capability_key) DO NOTHING;

INSERT INTO provider_registry(provider_key, capability_key, provider_type, base_url, runtime_profile, enabled, priority)
VALUES ('planning-pass1-local', 'water_yield', 'HTTP', 'planning-pass1', 'docker-local', TRUE, 100)
ON CONFLICT (provider_key) DO NOTHING;
