package com.sage.backend.mapper;

import com.sage.backend.model.TaskState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskStateMapper {

    @Insert("""
            INSERT INTO task_state(
                task_id,
                user_id,
                current_state,
                state_version,
                user_query,
                pass1_result_json,
                passb_result_json,
                slot_bindings_summary_json,
                args_draft_summary_json,
                validation_summary_json,
                input_chain_status,
                job_id,
                pass2_result_json,
                result_object_summary_json,
                result_bundle_summary_json,
                final_explanation_summary_json,
                last_failure_summary_json,
                waiting_context_json,
                waiting_reason_type,
                resume_payload_json,
                resume_attempt_count,
                active_attempt_no,
                waiting_since
            )
            VALUES(
                #{taskId},
                #{userId},
                #{currentState},
                #{stateVersion},
                #{userQuery},
                #{pass1ResultJson},
                #{passbResultJson},
                #{slotBindingsSummaryJson},
                #{argsDraftSummaryJson},
                #{validationSummaryJson},
                #{inputChainStatus},
                #{jobId},
                #{pass2ResultJson},
                #{resultObjectSummaryJson},
                #{resultBundleSummaryJson},
                #{finalExplanationSummaryJson},
                #{lastFailureSummaryJson},
                #{waitingContextJson},
                #{waitingReasonType},
                #{resumePayloadJson},
                #{resumeAttemptCount},
                #{activeAttemptNo},
                #{waitingSince}
            )
            """)
    int insert(TaskState taskState);

    @Update("""
            UPDATE task_state
            SET current_state = #{newState},
                state_version = state_version + 1,
                updated_at = NOW()
            WHERE task_id = #{taskId}
              AND state_version = #{expectedVersion}
            """)
    int updateState(
            @Param("taskId") String taskId,
            @Param("expectedVersion") int expectedVersion,
            @Param("newState") String newState
    );

    @Update("""
            UPDATE task_state
            SET current_state = #{newState},
                state_version = state_version + 1,
                pass1_result_json = #{pass1ResultJson},
                updated_at = NOW()
            WHERE task_id = #{taskId}
              AND state_version = #{expectedVersion}
            """)
    int updateStateAndPass1(
            @Param("taskId") String taskId,
            @Param("expectedVersion") int expectedVersion,
            @Param("newState") String newState,
            @Param("pass1ResultJson") String pass1ResultJson
    );

    @Update("""
            UPDATE task_state
            SET current_state = #{newState},
                state_version = state_version + 1,
                passb_result_json = #{passbResultJson},
                slot_bindings_summary_json = #{slotBindingsSummaryJson},
                args_draft_summary_json = #{argsDraftSummaryJson},
                validation_summary_json = #{validationSummaryJson},
                input_chain_status = #{inputChainStatus},
                waiting_context_json = NULL,
                waiting_reason_type = NULL,
                waiting_since = NULL,
                updated_at = NOW()
            WHERE task_id = #{taskId}
              AND state_version = #{expectedVersion}
            """)
    int updateStateWithInputChain(
            @Param("taskId") String taskId,
            @Param("expectedVersion") int expectedVersion,
            @Param("newState") String newState,
            @Param("passbResultJson") String passbResultJson,
            @Param("slotBindingsSummaryJson") String slotBindingsSummaryJson,
            @Param("argsDraftSummaryJson") String argsDraftSummaryJson,
            @Param("validationSummaryJson") String validationSummaryJson,
            @Param("inputChainStatus") String inputChainStatus
    );

    @Update("""
            UPDATE task_state
            SET current_state = #{newState},
                state_version = state_version + 1,
                pass2_result_json = #{pass2ResultJson},
                job_id = #{jobId},
                waiting_context_json = NULL,
                waiting_reason_type = NULL,
                updated_at = NOW()
            WHERE task_id = #{taskId}
              AND state_version = #{expectedVersion}
            """)
    int updateStateWithPass2AndJob(
            @Param("taskId") String taskId,
            @Param("expectedVersion") int expectedVersion,
            @Param("newState") String newState,
            @Param("pass2ResultJson") String pass2ResultJson,
            @Param("jobId") String jobId
    );

    @Update("""
            UPDATE task_state
            SET current_state = #{newState},
                state_version = state_version + 1,
                waiting_context_json = #{waitingContextJson},
                waiting_reason_type = #{waitingReasonType},
                waiting_since = #{waitingSince},
                updated_at = NOW()
            WHERE task_id = #{taskId}
              AND state_version = #{expectedVersion}
            """)
    int updateStateWithWaitingContext(
            @Param("taskId") String taskId,
            @Param("expectedVersion") int expectedVersion,
            @Param("newState") String newState,
            @Param("waitingContextJson") String waitingContextJson,
            @Param("waitingReasonType") String waitingReasonType,
            @Param("waitingSince") java.time.OffsetDateTime waitingSince
    );

    @Update("""
            UPDATE task_state
            SET waiting_context_json = #{waitingContextJson},
                waiting_reason_type = #{waitingReasonType},
                waiting_since = COALESCE(waiting_since, NOW()),
                updated_at = NOW()
            WHERE task_id = #{taskId}
            """)
    int updateWaitingContext(
            @Param("taskId") String taskId,
            @Param("waitingContextJson") String waitingContextJson,
            @Param("waitingReasonType") String waitingReasonType
    );

    @Update("""
            UPDATE task_state
            SET current_state = #{newState},
                state_version = state_version + 1,
                resume_payload_json = #{resumePayloadJson},
                resume_attempt_count = #{resumeAttemptCount},
                active_attempt_no = #{activeAttemptNo},
                waiting_context_json = NULL,
                waiting_reason_type = NULL,
                waiting_since = NULL,
                updated_at = NOW()
            WHERE task_id = #{taskId}
              AND state_version = #{expectedVersion}
            """)
    int acceptResume(
            @Param("taskId") String taskId,
            @Param("expectedVersion") int expectedVersion,
            @Param("newState") String newState,
            @Param("resumePayloadJson") String resumePayloadJson,
            @Param("resumeAttemptCount") int resumeAttemptCount,
            @Param("activeAttemptNo") int activeAttemptNo
    );

    @Update("""
            UPDATE task_state
            SET result_object_summary_json = #{resultObjectSummaryJson},
                updated_at = NOW()
            WHERE task_id = #{taskId}
            """)
    int updateResultObjectSummary(
            @Param("taskId") String taskId,
            @Param("resultObjectSummaryJson") String resultObjectSummaryJson
    );

    @Update("""
            UPDATE task_state
            SET result_bundle_summary_json = #{resultBundleSummaryJson},
                final_explanation_summary_json = #{finalExplanationSummaryJson},
                last_failure_summary_json = #{lastFailureSummaryJson},
                result_object_summary_json = #{resultObjectSummaryJson},
                updated_at = NOW()
            WHERE task_id = #{taskId}
            """)
    int updateOutputSummaries(
            @Param("taskId") String taskId,
            @Param("resultBundleSummaryJson") String resultBundleSummaryJson,
            @Param("finalExplanationSummaryJson") String finalExplanationSummaryJson,
            @Param("lastFailureSummaryJson") String lastFailureSummaryJson,
            @Param("resultObjectSummaryJson") String resultObjectSummaryJson
    );

    @Select("""
            SELECT task_id,
                   user_id,
                   current_state,
                   state_version,
                   user_query,
                   pass1_result_json,
                   passb_result_json,
                   slot_bindings_summary_json,
                   args_draft_summary_json,
                   validation_summary_json,
                   input_chain_status,
                   job_id,
                   pass2_result_json,
                   result_object_summary_json,
                   result_bundle_summary_json,
                   final_explanation_summary_json,
                   last_failure_summary_json,
                   waiting_context_json,
                   waiting_reason_type,
                   resume_payload_json,
                   resume_attempt_count,
                   active_attempt_no,
                   waiting_since,
                   created_at,
                   updated_at
            FROM task_state
            WHERE task_id = #{taskId}
            """)
    TaskState findByTaskId(@Param("taskId") String taskId);
}
