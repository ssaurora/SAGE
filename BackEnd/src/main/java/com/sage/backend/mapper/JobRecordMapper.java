package com.sage.backend.mapper;

import com.sage.backend.model.JobRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface JobRecordMapper {

    @Insert("""
            INSERT INTO job_record(
                job_id,
                task_id,
                attempt_no,
                job_state,
                execution_graph_json,
                runtime_assertions_json,
                planning_pass2_summary_json,
                workspace_id,
                provider_key,
                capability_key,
                runtime_profile,
                result_bundle_json,
                final_explanation_json,
                failure_summary_json,
                docker_runtime_evidence_json,
                workspace_summary_json,
                artifact_catalog_json,
                cancel_requested_at,
                cancelled_at,
                cancel_reason,
                accepted_at,
                last_heartbeat_at
            )
            VALUES(
                #{jobId},
                #{taskId},
                #{attemptNo},
                #{jobState},
                #{executionGraphJson},
                #{runtimeAssertionsJson},
                #{planningPass2SummaryJson},
                #{workspaceId},
                #{providerKey},
                #{capabilityKey},
                #{runtimeProfile},
                #{resultBundleJson},
                #{finalExplanationJson},
                #{failureSummaryJson},
                #{dockerRuntimeEvidenceJson},
                #{workspaceSummaryJson},
                #{artifactCatalogJson},
                #{cancelRequestedAt},
                #{cancelledAt},
                #{cancelReason},
                #{acceptedAt},
                #{lastHeartbeatAt}
            )
            """)
    int insert(JobRecord jobRecord);

    @Select("""
            SELECT job_id,
                   task_id,
                   attempt_no,
                   job_state,
                   execution_graph_json,
                   runtime_assertions_json,
                   planning_pass2_summary_json,
                   workspace_id,
                   provider_key,
                   capability_key,
                   runtime_profile,
                   result_object_json,
                   result_bundle_json,
                   final_explanation_json,
                   failure_summary_json,
                   docker_runtime_evidence_json,
                   workspace_summary_json,
                   artifact_catalog_json,
                   error_json,
                   cancel_requested_at,
                   cancelled_at,
                   cancel_reason,
                   accepted_at,
                   started_at,
                   finished_at,
                   last_heartbeat_at,
                   created_at,
                   updated_at
            FROM job_record
            WHERE task_id = #{taskId}
            ORDER BY attempt_no DESC, created_at DESC
            LIMIT 1
            """)
    JobRecord findByTaskId(@Param("taskId") String taskId);

    @Select("""
            SELECT job_id,
                   task_id,
                   attempt_no,
                   job_state,
                   execution_graph_json,
                   runtime_assertions_json,
                   planning_pass2_summary_json,
                   workspace_id,
                   provider_key,
                   capability_key,
                   runtime_profile,
                   result_object_json,
                   result_bundle_json,
                   final_explanation_json,
                   failure_summary_json,
                   docker_runtime_evidence_json,
                   workspace_summary_json,
                   artifact_catalog_json,
                   error_json,
                   cancel_requested_at,
                   cancelled_at,
                   cancel_reason,
                   accepted_at,
                   started_at,
                   finished_at,
                   last_heartbeat_at,
                   created_at,
                   updated_at
            FROM job_record
            WHERE job_id = #{jobId}
            """)
    JobRecord findByJobId(@Param("jobId") String jobId);

    @Select("""
            SELECT job_id,
                   task_id,
                   attempt_no,
                   job_state,
                   execution_graph_json,
                   runtime_assertions_json,
                   planning_pass2_summary_json,
                   workspace_id,
                   provider_key,
                   capability_key,
                   runtime_profile,
                   result_object_json,
                   result_bundle_json,
                   final_explanation_json,
                   failure_summary_json,
                   docker_runtime_evidence_json,
                   workspace_summary_json,
                   artifact_catalog_json,
                   error_json,
                   cancel_requested_at,
                   cancelled_at,
                   cancel_reason,
                   accepted_at,
                   started_at,
                   finished_at,
                   last_heartbeat_at,
                   created_at,
                   updated_at
            FROM job_record
            WHERE job_state IN ('ACCEPTED', 'RUNNING')
            ORDER BY updated_at ASC
            """)
    List<JobRecord> findActiveJobs();

    @Update("""
            UPDATE job_record
            SET job_state = #{jobState},
                started_at = #{startedAt},
                finished_at = #{finishedAt},
                last_heartbeat_at = #{lastHeartbeatAt},
                result_object_json = #{resultObjectJson},
                result_bundle_json = #{resultBundleJson},
                final_explanation_json = #{finalExplanationJson},
                failure_summary_json = #{failureSummaryJson},
                docker_runtime_evidence_json = #{dockerRuntimeEvidenceJson},
                workspace_summary_json = #{workspaceSummaryJson},
                artifact_catalog_json = #{artifactCatalogJson},
                error_json = #{errorJson},
                cancel_requested_at = #{cancelRequestedAt},
                cancelled_at = #{cancelledAt},
                cancel_reason = #{cancelReason},
                updated_at = #{updatedAt}
            WHERE job_id = #{jobId}
            """)
    int updateRuntimeSnapshot(
            @Param("jobId") String jobId,
            @Param("jobState") String jobState,
            @Param("startedAt") OffsetDateTime startedAt,
            @Param("finishedAt") OffsetDateTime finishedAt,
            @Param("lastHeartbeatAt") OffsetDateTime lastHeartbeatAt,
            @Param("resultObjectJson") String resultObjectJson,
            @Param("resultBundleJson") String resultBundleJson,
            @Param("finalExplanationJson") String finalExplanationJson,
            @Param("failureSummaryJson") String failureSummaryJson,
            @Param("dockerRuntimeEvidenceJson") String dockerRuntimeEvidenceJson,
            @Param("workspaceSummaryJson") String workspaceSummaryJson,
            @Param("artifactCatalogJson") String artifactCatalogJson,
            @Param("errorJson") String errorJson,
            @Param("cancelRequestedAt") OffsetDateTime cancelRequestedAt,
            @Param("cancelledAt") OffsetDateTime cancelledAt,
            @Param("cancelReason") String cancelReason,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Select("""
            SELECT job_id,
                   task_id,
                   attempt_no,
                   job_state,
                   execution_graph_json,
                   runtime_assertions_json,
                   planning_pass2_summary_json,
                   workspace_id,
                   provider_key,
                   capability_key,
                   runtime_profile,
                   result_object_json,
                   result_bundle_json,
                   final_explanation_json,
                   failure_summary_json,
                   docker_runtime_evidence_json,
                   workspace_summary_json,
                   artifact_catalog_json,
                   error_json,
                   cancel_requested_at,
                   cancelled_at,
                   cancel_reason,
                   accepted_at,
                   started_at,
                   finished_at,
                   last_heartbeat_at,
                   created_at,
                   updated_at
            FROM job_record
            WHERE task_id = #{taskId}
              AND attempt_no = #{attemptNo}
            LIMIT 1
            """)
    JobRecord findByTaskIdAndAttemptNo(@Param("taskId") String taskId, @Param("attemptNo") int attemptNo);
}
