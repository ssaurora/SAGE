package com.sage.backend.model;

public enum SessionMessageType {
    user_goal,
    assistant_understanding,
    clarification_request,
    user_clarification_answer,
    missing_input_request,
    user_reply,
    upload_ack,
    progress_update,
    waiting_notice,
    resume_notice,
    result_summary,
    failure_explanation,
    next_step_guidance,
    system_note
}
