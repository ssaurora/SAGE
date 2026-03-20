package com.sage.backend.mapper;

import com.sage.backend.model.WorkspaceRegistry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface WorkspaceRegistryMapper {
    @Insert("""
            INSERT INTO workspace_registry(
                workspace_id,
                task_id,
                job_id,
                attempt_no,
                runtime_profile,
                container_name,
                host_workspace_path,
                archive_path,
                workspace_state,
                started_at,
                finished_at,
                cleaned_at,
                archived_at,
                updated_at
            ) VALUES(
                #{workspaceId},
                #{taskId},
                #{jobId},
                #{attemptNo},
                #{runtimeProfile},
                #{containerName},
                #{hostWorkspacePath},
                #{archivePath},
                #{workspaceState},
                #{startedAt},
                #{finishedAt},
                #{cleanedAt},
                #{archivedAt},
                NOW()
            )
            """)
    int insert(WorkspaceRegistry record);

    @Select("""
            SELECT workspace_id, task_id, job_id, attempt_no, runtime_profile, container_name,
                   host_workspace_path, archive_path, workspace_state, created_at, started_at,
                   finished_at, cleaned_at, archived_at, updated_at
            FROM workspace_registry
            WHERE workspace_id = #{workspaceId}
            """)
    WorkspaceRegistry findByWorkspaceId(@Param("workspaceId") String workspaceId);

    @Select("""
            SELECT workspace_id, task_id, job_id, attempt_no, runtime_profile, container_name,
                   host_workspace_path, archive_path, workspace_state, created_at, started_at,
                   finished_at, cleaned_at, archived_at, updated_at
            FROM workspace_registry
            WHERE task_id = #{taskId}
            ORDER BY attempt_no DESC, created_at DESC
            """)
    List<WorkspaceRegistry> findByTaskId(@Param("taskId") String taskId);

    @Update("""
            UPDATE workspace_registry
            SET container_name = #{containerName},
                host_workspace_path = #{hostWorkspacePath},
                archive_path = #{archivePath},
                workspace_state = #{workspaceState},
                started_at = #{startedAt},
                finished_at = #{finishedAt},
                cleaned_at = #{cleanedAt},
                archived_at = #{archivedAt},
                updated_at = #{updatedAt}
            WHERE workspace_id = #{workspaceId}
            """)
    int updateSnapshot(
            @Param("workspaceId") String workspaceId,
            @Param("containerName") String containerName,
            @Param("hostWorkspacePath") String hostWorkspacePath,
            @Param("archivePath") String archivePath,
            @Param("workspaceState") String workspaceState,
            @Param("startedAt") OffsetDateTime startedAt,
            @Param("finishedAt") OffsetDateTime finishedAt,
            @Param("cleanedAt") OffsetDateTime cleanedAt,
            @Param("archivedAt") OffsetDateTime archivedAt,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
