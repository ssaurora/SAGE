package com.sage.backend.mapper;

import com.sage.backend.model.ResultBundleRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ResultBundleRecordMapper {
    @Insert("""
            INSERT INTO result_bundle_record(
                result_bundle_id,
                task_id,
                job_id,
                attempt_no,
                manifest_id,
                workspace_id,
                result_bundle_json,
                final_explanation_json,
                summary_text
            ) VALUES(
                #{resultBundleId},
                #{taskId},
                #{jobId},
                #{attemptNo},
                #{manifestId},
                #{workspaceId},
                #{resultBundleJson},
                #{finalExplanationJson},
                #{summaryText}
            )
            """)
    int insert(ResultBundleRecord record);

    @Select("""
            SELECT result_bundle_id, task_id, job_id, attempt_no, manifest_id, workspace_id,
                   result_bundle_json, final_explanation_json, summary_text, created_at
            FROM result_bundle_record
            WHERE task_id = #{taskId}
            ORDER BY attempt_no DESC, created_at DESC
            """)
    List<ResultBundleRecord> findByTaskId(@Param("taskId") String taskId);

    @Select("""
            SELECT result_bundle_id, task_id, job_id, attempt_no, manifest_id, workspace_id,
                   result_bundle_json, final_explanation_json, summary_text, created_at
            FROM result_bundle_record
            WHERE job_id = #{jobId}
            LIMIT 1
            """)
    ResultBundleRecord findByJobId(@Param("jobId") String jobId);
}
