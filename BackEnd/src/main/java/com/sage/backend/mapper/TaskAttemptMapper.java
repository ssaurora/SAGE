package com.sage.backend.mapper;

import com.sage.backend.model.TaskAttempt;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TaskAttemptMapper {

    @Insert("""
            INSERT INTO task_attempt(
                task_id,
                attempt_no,
                trigger,
                job_id,
                status_snapshot_json,
                finished_at
            ) VALUES(
                #{taskId},
                #{attemptNo},
                #{trigger},
                #{jobId},
                #{statusSnapshotJson},
                #{finishedAt}
            )
            """)
    int insert(TaskAttempt taskAttempt);

    @Select("""
            SELECT id,
                   task_id,
                   attempt_no,
                   trigger,
                   job_id,
                   status_snapshot_json,
                   created_at,
                   finished_at
            FROM task_attempt
            WHERE task_id = #{taskId}
              AND attempt_no = #{attemptNo}
            LIMIT 1
            """)
    TaskAttempt findByTaskIdAndAttemptNo(@Param("taskId") String taskId, @Param("attemptNo") int attemptNo);

    @Select("""
            SELECT id,
                   task_id,
                   attempt_no,
                   trigger,
                   job_id,
                   status_snapshot_json,
                   created_at,
                   finished_at
            FROM task_attempt
            WHERE task_id = #{taskId}
            ORDER BY attempt_no ASC, created_at ASC
            """)
    List<TaskAttempt> findByTaskId(@Param("taskId") String taskId);

    @Update("""
            UPDATE task_attempt
            SET job_id = #{jobId},
                status_snapshot_json = #{statusSnapshotJson},
                finished_at = #{finishedAt}
            WHERE task_id = #{taskId}
              AND attempt_no = #{attemptNo}
            """)
    int updateSnapshotAndJob(
            @Param("taskId") String taskId,
            @Param("attemptNo") int attemptNo,
            @Param("jobId") String jobId,
            @Param("statusSnapshotJson") String statusSnapshotJson,
            @Param("finishedAt") java.time.OffsetDateTime finishedAt
    );
}
