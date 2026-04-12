CREATE TABLE IF NOT EXISTS analysis_session (
    session_id VARCHAR(128) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    title VARCHAR(255),
    user_goal TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    scene_id VARCHAR(128),
    current_task_id VARCHAR(64),
    latest_result_bundle_id VARCHAR(64),
    current_required_user_action_json TEXT,
    session_summary_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_analysis_session_status CHECK (status IN ('RUNNING', 'WAITING_USER', 'READY_RESULT', 'FAILED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS session_message (
    message_id VARCHAR(128) PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL REFERENCES analysis_session(session_id),
    task_id VARCHAR(64),
    result_bundle_id VARCHAR(64),
    role VARCHAR(16) NOT NULL,
    message_type VARCHAR(64) NOT NULL,
    content_json TEXT,
    attachment_refs_json TEXT,
    action_schema_json TEXT,
    related_object_refs_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_session_message_role CHECK (role IN ('user', 'assistant', 'system')),
    CONSTRAINT chk_session_message_type CHECK (
        message_type IN (
            'user_goal',
            'assistant_understanding',
            'clarification_request',
            'user_clarification_answer',
            'missing_input_request',
            'user_reply',
            'upload_ack',
            'progress_update',
            'waiting_notice',
            'resume_notice',
            'result_summary',
            'failure_explanation',
            'next_step_guidance',
            'system_note'
        )
    )
);

ALTER TABLE task_state
    ADD COLUMN IF NOT EXISTS session_id VARCHAR(128);

INSERT INTO analysis_session(
    session_id,
    user_id,
    title,
    user_goal,
    status,
    current_task_id,
    latest_result_bundle_id,
    current_required_user_action_json,
    session_summary_json,
    created_at,
    updated_at
)
SELECT
    'sess_' || ts.task_id,
    ts.user_id,
    LEFT(ts.user_query, 255),
    ts.user_query,
    CASE
        WHEN ts.current_state = 'WAITING_USER' THEN 'WAITING_USER'
        WHEN ts.current_state = 'SUCCEEDED' THEN 'READY_RESULT'
        WHEN ts.current_state IN ('FAILED', 'STATE_CORRUPTED') THEN 'FAILED'
        WHEN ts.current_state = 'CANCELLED' THEN 'CANCELLED'
        ELSE 'RUNNING'
    END,
    ts.task_id,
    ts.latest_result_bundle_id,
    CASE
        WHEN ts.current_state = 'WAITING_USER' THEN COALESCE(
            ts.waiting_context_json,
            jsonb_build_object('status', 'WAITING_USER', 'note', 'Legacy waiting session migrated from task_state')::text
        )
        ELSE NULL
    END,
    jsonb_build_object(
        'task_id', ts.task_id,
        'task_state', ts.current_state,
        'latest_result_bundle_id', ts.latest_result_bundle_id
    )::text,
    ts.created_at,
    ts.updated_at
FROM task_state ts
ON CONFLICT (session_id) DO NOTHING;

UPDATE task_state
SET session_id = 'sess_' || task_id
WHERE session_id IS NULL;

ALTER TABLE task_state
    ALTER COLUMN session_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_task_state_session'
    ) THEN
        ALTER TABLE task_state
            ADD CONSTRAINT fk_task_state_session
            FOREIGN KEY (session_id) REFERENCES analysis_session(session_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_analysis_session_user_updated_at
    ON analysis_session (user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_analysis_session_status_updated_at
    ON analysis_session (status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_analysis_session_scene_updated_at
    ON analysis_session (scene_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_state_session_updated_at
    ON task_state (session_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_session_message_session_created_at
    ON session_message (session_id, created_at ASC);

INSERT INTO session_message(
    message_id,
    session_id,
    task_id,
    result_bundle_id,
    role,
    message_type,
    content_json,
    related_object_refs_json,
    created_at
)
SELECT
    'msg_' || ts.task_id || '_goal',
    'sess_' || ts.task_id,
    ts.task_id,
    ts.latest_result_bundle_id,
    'user',
    'user_goal',
    jsonb_build_object(
        'text', ts.user_query,
        'source', 'legacy_task_migration'
    )::text,
    jsonb_build_object('task_id', ts.task_id)::text,
    ts.created_at
FROM task_state ts
ON CONFLICT (message_id) DO NOTHING;

INSERT INTO session_message(
    message_id,
    session_id,
    task_id,
    result_bundle_id,
    role,
    message_type,
    content_json,
    related_object_refs_json,
    created_at
)
SELECT
    'msg_' || ts.task_id || '_waiting',
    'sess_' || ts.task_id,
    ts.task_id,
    ts.latest_result_bundle_id,
    'assistant',
    'waiting_notice',
    COALESCE(
        ts.waiting_context_json,
        jsonb_build_object('text', 'Legacy task was waiting for user input during migration')::text
    ),
    jsonb_build_object('task_id', ts.task_id)::text,
    COALESCE(ts.waiting_since, ts.updated_at, ts.created_at)
FROM task_state ts
WHERE ts.current_state = 'WAITING_USER'
ON CONFLICT (message_id) DO NOTHING;

INSERT INTO session_message(
    message_id,
    session_id,
    task_id,
    result_bundle_id,
    role,
    message_type,
    content_json,
    related_object_refs_json,
    created_at
)
SELECT
    'msg_' || ts.task_id || '_result',
    'sess_' || ts.task_id,
    ts.task_id,
    ts.latest_result_bundle_id,
    'assistant',
    'result_summary',
    COALESCE(
        ts.final_explanation_summary_json,
        ts.result_bundle_summary_json,
        jsonb_build_object('text', 'Legacy task completed before session migration')::text
    ),
    jsonb_build_object(
        'task_id', ts.task_id,
        'result_bundle_id', ts.latest_result_bundle_id
    )::text,
    ts.updated_at
FROM task_state ts
WHERE ts.current_state = 'SUCCEEDED'
ON CONFLICT (message_id) DO NOTHING;

INSERT INTO session_message(
    message_id,
    session_id,
    task_id,
    result_bundle_id,
    role,
    message_type,
    content_json,
    related_object_refs_json,
    created_at
)
SELECT
    'msg_' || ts.task_id || '_failure',
    'sess_' || ts.task_id,
    ts.task_id,
    ts.latest_result_bundle_id,
    'assistant',
    'failure_explanation',
    COALESCE(
        ts.last_failure_summary_json,
        jsonb_build_object('text', 'Legacy task failed before session migration')::text
    ),
    jsonb_build_object('task_id', ts.task_id)::text,
    ts.updated_at
FROM task_state ts
WHERE ts.current_state IN ('FAILED', 'STATE_CORRUPTED')
ON CONFLICT (message_id) DO NOTHING;
