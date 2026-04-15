ALTER TABLE session_message
    DROP CONSTRAINT IF EXISTS chk_session_message_type;

ALTER TABLE session_message
    ADD CONSTRAINT chk_session_message_type CHECK (
        message_type IN (
            'user_goal',
            'assistant_understanding',
            'assistant_execution_brief',
            'clarification_request',
            'user_clarification_answer',
            'missing_input_request',
            'user_reply',
            'upload_ack',
            'progress_update',
            'waiting_notice',
            'resume_notice',
            'result_summary',
            'result_ready',
            'assistant_reviewing',
            'assistant_final_explanation',
            'failure_explanation',
            'next_step_guidance',
            'system_note'
        )
    );
