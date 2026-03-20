package com.sage.backend.mapper;

import com.sage.backend.model.ArtifactIndexRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ArtifactIndexMapper {
    @Insert("""
            INSERT INTO artifact_index(
                artifact_id,
                task_id,
                job_id,
                attempt_no,
                workspace_id,
                result_bundle_id,
                artifact_role,
                logical_name,
                relative_path,
                absolute_path,
                content_type,
                size_bytes,
                sha256
            ) VALUES(
                #{artifactId},
                #{taskId},
                #{jobId},
                #{attemptNo},
                #{workspaceId},
                #{resultBundleId},
                #{artifactRole},
                #{logicalName},
                #{relativePath},
                #{absolutePath},
                #{contentType},
                #{sizeBytes},
                #{sha256}
            )
            """)
    int insert(ArtifactIndexRecord record);

    @Select("""
            SELECT artifact_id, task_id, job_id, attempt_no, workspace_id, result_bundle_id,
                   artifact_role, logical_name, relative_path, absolute_path, content_type,
                   size_bytes, sha256, created_at
            FROM artifact_index
            WHERE task_id = #{taskId}
            ORDER BY attempt_no DESC, created_at ASC
            """)
    List<ArtifactIndexRecord> findByTaskId(@Param("taskId") String taskId);

    @Select("""
            SELECT artifact_id, task_id, job_id, attempt_no, workspace_id, result_bundle_id,
                   artifact_role, logical_name, relative_path, absolute_path, content_type,
                   size_bytes, sha256, created_at
            FROM artifact_index
            WHERE job_id = #{jobId}
            ORDER BY created_at ASC
            """)
    List<ArtifactIndexRecord> findByJobId(@Param("jobId") String jobId);
}
