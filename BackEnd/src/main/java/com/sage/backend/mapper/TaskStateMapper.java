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
                goal_parse_json,
                skill_route_json,
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
                resume_txn_json,
                resume_attempt_count,
                active_attempt_no,
                active_manifest_id,
                active_manifest_version,
                planning_revision,
                checkpoint_version,
                inventory_version,
                cognition_verdict,
                corruption_reason,
                latest_result_bundle_id,
                latest_workspace_id,
                corrupted_since,
                waiting_since
            )
            VALUES(
                #{taskId},
                #{userId},
                #{currentState},
                #{stateVersion},
                #{userQuery},
                #{pass1ResultJson},
                #{goalParseJson},
                #{skillRouteJson},
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
                #{resumeTxnJson},
                #{resumeAttemptCount},
                #{activeAttemptNo},
                #{activeManifestId},
                #{activeManifestVersion},
                #{planningRevision},
                #{checkpointVersion},
                #{inventoryVersion},
                #{cognitionVerdict},
                #{corruptionReason},
                #{latestResultBundleId},
                #{latestWorkspaceId},
                #{corruptedSince},
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
            SET goal_parse_json = #{goalParseJson},
                skill_route_json = #{skillRouteJson},
                updated_at = NOW()
            WHERE task_id = #{taskId}
            """)
    int updateGoalAndRoute(
            @Param("taskId") String taskId,
            @Param("goalParseJson") String goalParseJson,
            @Param("skillRouteJson") String skillRouteJson
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
            SET passb_result_json = #{passbResultJson},
                slot_bindings_summary_json = #{slotBindingsSummaryJson},
                args_draft_summary_json = #{argsDraftSummaryJson},
                validation_summary_json = #{validationSummaryJson},
                input_chain_status = #{inputChainStatus},
                updated_at = NOW()
            WHERE task_id = #{taskId}
            """)
    int updateInputChainSnapshot(
            @Param("taskId") String taskId,
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
            SET resume_txn_json = #{resumeTxnJson},
                updated_at = NOW()
            WHERE task_id = #{taskId}
            """)
    int updateResumeTransaction(
            @Param("taskId") String taskId,
            @Param("resumeTxnJson") String resumeTxnJson
    );

    @Update("""
            UPDATE task_state
            SET inventory_version = inventory_version + 1,
                updated_at = NOW()
            WHERE task_id = #{taskId}
            """)
    int incrementInventoryVersion(@Param("taskId") String taskId);

    @Update("""
            UPDATE task_state
            SET current_state = #{newState},
                state_version = state_version + 1,
                resume_payload_json = #{resumePayloadJson},
                resume_txn_json = #{resumeTxnJson},
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
            @Param("resumeTxnJson") String resumeTxnJson,
            @Param("resumeAttemptCount") int resumeAttemptCount,
            @Param("activeAttemptNo") int activeAttemptNo
    );

    @Update("""
            UPDATE task_state
            SET current_state = #{newState},
                state_version = state_version + 1,
                pass2_result_json = #{pass2ResultJson},
                job_id = #{jobId},
                active_manifest_id = #{manifestId},
                active_manifest_version = #{manifestVersion},
                planning_revision = #{planningRevision},
                checkpoint_version = #{checkpointVersion},
                inventory_version = #{inventoryVersion},
                cognition_verdict = #{cognitionVerdict},
                resume_txn_json = #{resumeTxnJson},
                corruption_reason = NULL,
                corrupted_since = NULL,
                waiting_context_json = NULL,
                waiting_reason_type = NULL,
                waiting_since = NULL,
                updated_at = NOW()
            WHERE task_id = #{taskId}
              AND state_version = #{expectedVersion}
            """)
    int commitQueuedWithGovernance(
            @Param("taskId") String taskId,
            @Param("expectedVersion") int expectedVersion,
            @Param("newState") String newState,
            @Param("pass2ResultJson") String pass2ResultJson,
            @Param("jobId") String jobId,
            @Param("manifestId") String manifestId,
            @Param("manifestVersion") int manifestVersion,
            @Param("planningRevision") int planningRevision,
            @Param("checkpointVersion") int checkpointVersion,
            @Param("inventoryVersion") int inventoryVersion,
            @Param("cognitionVerdict") String cognitionVerdict,
            @Param("resumeTxnJson") String resumeTxnJson
    );

    @Update("""
            UPDATE task_state
            SET cognition_verdict = #{cognitionVerdict},
                updated_at = NOW()
            WHERE task_id = #{taskId}
            """)
    int updateCognitionVerdict(
            @Param("taskId") String taskId,
            @Param("cognitionVerdict") String cognitionVerdict
    );

    @Update("""
            UPDATE task_state
            SET current_state = #{newState},
                state_version = state_version + 1,
                waiting_context_json = #{waitingContextJson},
                waiting_reason_type = #{waitingReasonType},
                waiting_since = COALESCE(#{waitingSince}, NOW()),
                resume_txn_json = #{resumeTxnJson},
                updated_at = NOW()
            WHERE task_id = #{taskId}
              AND state_version = #{expectedVersion}
            """)
    int rollbackResumeToWaiting(
            @Param("taskId") String taskId,
            @Param("expectedVersion") int expectedVersion,
            @Param("newState") String newState,
            @Param("waitingContextJson") String waitingContextJson,
            @Param("waitingReasonType") String waitingReasonType,
            @Param("waitingSince") java.time.OffsetDateTime waitingSince,
            @Param("resumeTxnJson") String resumeTxnJson
    );

    @Update("""
            UPDATE task_state
            SET current_state = #{newState},
                state_version = state_version + 1,
                corruption_reason = #{corruptionReason},
                corrupted_since = #{corruptedSince},
                resume_txn_json = #{resumeTxnJson},
                updated_at = NOW()
            WHERE task_id = #{taskId}
              AND state_version = #{expectedVersion}
            """)
    int markCorrupted(
            @Param("taskId") String taskId,
            @Param("expectedVersion") int expectedVersion,
            @Param("newState") String newState,
            @Param("corruptionReason") String corruptionReason,
            @Param("corruptedSince") java.time.OffsetDateTime corruptedSince,
            @Param("resumeTxnJson") String resumeTxnJson
    );

    @Update("""
            UPDATE task_state
            SET current_state = #{newState},
                state_version = state_version + 1,
                active_manifest_id = #{manifestId},
                active_manifest_version = #{manifestVersion},
                active_attempt_no = #{attemptNo},
                planning_revision = #{planningRevision},
                checkpoint_version = #{checkpointVersion},
                job_id = NULL,
                pass2_result_json = NULL,
                corruption_reason = NULL,
                corrupted_since = NULL,
                resume_txn_json = #{resumeTxnJson},
                updated_at = NOW()
            WHERE task_id = #{taskId}
              AND state_version = #{expectedVersion}
            """)
    int forceRevertCheckpoint(
            @Param("taskId") String taskId,
            @Param("expectedVersion") int expectedVersion,
            @Param("newState") String newState,
            @Param("manifestId") String manifestId,
            @Param("manifestVersion") int manifestVersion,
            @Param("attemptNo") int attemptNo,
            @Param("planningRevision") int planningRevision,
            @Param("checkpointVersion") int checkpointVersion,
            @Param("resumeTxnJson") String resumeTxnJson
    );

    @Update("""
            UPDATE task_state
            SET active_manifest_id = #{manifestId},
                active_manifest_version = #{manifestVersion},
                updated_at = NOW()
            WHERE task_id = #{taskId}
            """)
    int updateActiveManifest(
            @Param("taskId") String taskId,
            @Param("manifestId") String manifestId,
            @Param("manifestVersion") int manifestVersion
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

    @Update("""
            UPDATE task_state
            SET latest_result_bundle_id = COALESCE(#{latestResultBundleId}, latest_result_bundle_id),
                latest_workspace_id = COALESCE(#{latestWorkspaceId}, latest_workspace_id),
                updated_at = NOW()
            WHERE task_id = #{taskId}
            """)
    int updateLatestTracePointers(
            @Param("taskId") String taskId,
            @Param("latestResultBundleId") String latestResultBundleId,
            @Param("latestWorkspaceId") String latestWorkspaceId
    );

    @Select("""
            SELECT task_id,
                   user_id,
                   current_state,
                   state_version,
                   user_query,
                   pass1_result_json,
                   goal_parse_json,
                   skill_route_json,
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
                   resume_txn_json,
                   resume_attempt_count,
                   active_attempt_no,
                   active_manifest_id,
                   active_manifest_version,
                   planning_revision,
                   checkpoint_version,
                   inventory_version,
                   cognition_verdict,
                   corruption_reason,
                   latest_result_bundle_id,
                   latest_workspace_id,
                   corrupted_since,
                   waiting_since,
                   created_at,
                   updated_at
            FROM task_state
            WHERE task_id = #{taskId}
            """)
    TaskState findByTaskId(@Param("taskId") String taskId);
}
