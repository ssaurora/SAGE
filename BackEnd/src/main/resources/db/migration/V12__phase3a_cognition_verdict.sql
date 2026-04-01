ALTER TABLE task_state
    ADD COLUMN IF NOT EXISTS cognition_verdict VARCHAR(64);
