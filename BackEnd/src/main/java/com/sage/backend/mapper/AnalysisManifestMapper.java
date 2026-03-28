package com.sage.backend.mapper;

import com.sage.backend.model.AnalysisManifest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AnalysisManifestMapper {

    @Insert("""
            INSERT INTO analysis_manifest(
                manifest_id,
                task_id,
                attempt_no,
                manifest_version,
                goal_parse_json,
                skill_route_json,
                freeze_status,
                planning_revision,
                checkpoint_version,
                graph_digest,
                planning_summary_json,
                capability_key,
                selected_template,
                template_version,
                logical_input_roles_json,
                slot_schema_view_json,
                slot_bindings_json,
                args_draft_json,
                validation_summary_json,
                execution_graph_json,
                runtime_assertions_json
            ) VALUES(
                #{manifestId},
                #{taskId},
                #{attemptNo},
                #{manifestVersion},
                #{goalParseJson},
                #{skillRouteJson},
                #{freezeStatus},
                #{planningRevision},
                #{checkpointVersion},
                #{graphDigest},
                #{planningSummaryJson},
                #{capabilityKey},
                #{selectedTemplate},
                #{templateVersion},
                #{logicalInputRolesJson},
                #{slotSchemaViewJson},
                #{slotBindingsJson},
                #{argsDraftJson},
                #{validationSummaryJson},
                #{executionGraphJson},
                #{runtimeAssertionsJson}
            )
            """)
    int insert(AnalysisManifest manifest);

    @org.apache.ibatis.annotations.Update("""
            UPDATE analysis_manifest
            SET freeze_status = #{freezeStatus}
            WHERE manifest_id = #{manifestId}
              AND freeze_status = #{expectedStatus}
            """)
    int updateFreezeStatus(
            @Param("manifestId") String manifestId,
            @Param("expectedStatus") String expectedStatus,
            @Param("freezeStatus") String freezeStatus
    );

    @Select("""
            SELECT manifest_id,
                   task_id,
                   attempt_no,
                   manifest_version,
                   goal_parse_json,
                   skill_route_json,
                   freeze_status,
                   planning_revision,
                   checkpoint_version,
                   graph_digest,
                   planning_summary_json,
                   capability_key,
                   selected_template,
                   template_version,
                   logical_input_roles_json,
                   slot_schema_view_json,
                   slot_bindings_json,
                   args_draft_json,
                   validation_summary_json,
                   execution_graph_json,
                   runtime_assertions_json,
                   created_at
            FROM analysis_manifest
            WHERE manifest_id = #{manifestId}
            LIMIT 1
            """)
    AnalysisManifest findByManifestId(@Param("manifestId") String manifestId);

    @Select("""
            SELECT manifest_id,
                   task_id,
                   attempt_no,
                   manifest_version,
                   goal_parse_json,
                   skill_route_json,
                   freeze_status,
                   planning_revision,
                   checkpoint_version,
                   graph_digest,
                   planning_summary_json,
                   capability_key,
                   selected_template,
                   template_version,
                   logical_input_roles_json,
                   slot_schema_view_json,
                   slot_bindings_json,
                   args_draft_json,
                   validation_summary_json,
                   execution_graph_json,
                   runtime_assertions_json,
                   created_at
            FROM analysis_manifest
            WHERE task_id = #{taskId}
              AND attempt_no = #{attemptNo}
            ORDER BY CASE WHEN freeze_status = 'FROZEN' THEN 0 ELSE 1 END,
                     manifest_version DESC,
                     created_at DESC
            LIMIT 1
            """)
    AnalysisManifest findLatestByTaskIdAndAttemptNo(@Param("taskId") String taskId, @Param("attemptNo") int attemptNo);

    @Select("""
            SELECT manifest_id,
                   task_id,
                   attempt_no,
                   manifest_version,
                   goal_parse_json,
                   skill_route_json,
                   freeze_status,
                   planning_revision,
                   checkpoint_version,
                   graph_digest,
                   planning_summary_json,
                   capability_key,
                   selected_template,
                   template_version,
                   logical_input_roles_json,
                   slot_schema_view_json,
                   slot_bindings_json,
                   args_draft_json,
                   validation_summary_json,
                   execution_graph_json,
                   runtime_assertions_json,
                   created_at
            FROM analysis_manifest
            WHERE task_id = #{taskId}
              AND checkpoint_version = #{checkpointVersion}
              AND freeze_status = 'FROZEN'
            ORDER BY manifest_version DESC, created_at DESC
            LIMIT 1
            """)
    AnalysisManifest findLatestFrozenByTaskIdAndCheckpointVersion(
            @Param("taskId") String taskId,
            @Param("checkpointVersion") int checkpointVersion
    );
}
