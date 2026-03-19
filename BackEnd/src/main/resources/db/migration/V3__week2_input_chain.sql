ALTER TABLE task_state
    ADD COLUMN IF NOT EXISTS passb_result_json TEXT,
    ADD COLUMN IF NOT EXISTS slot_bindings_summary_json TEXT,
    ADD COLUMN IF NOT EXISTS args_draft_summary_json TEXT,
    ADD COLUMN IF NOT EXISTS validation_summary_json TEXT,
    ADD COLUMN IF NOT EXISTS input_chain_status VARCHAR(16);

ALTER TABLE task_state
    DROP CONSTRAINT IF EXISTS chk_task_state_input_chain_status;

ALTER TABLE task_state
    ADD CONSTRAINT chk_task_state_input_chain_status
    CHECK (input_chain_status IS NULL OR input_chain_status IN ('COMPLETE', 'INCOMPLETE'));
