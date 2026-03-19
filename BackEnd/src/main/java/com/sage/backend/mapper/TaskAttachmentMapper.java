package com.sage.backend.mapper;

import com.sage.backend.model.TaskAttachment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskAttachmentMapper {

    @Insert("""
            INSERT INTO task_attachment(
                id,
                task_id,
                attempt_no,
                logical_slot,
                assignment_status,
                file_name,
                content_type,
                size_bytes,
                stored_path,
                checksum,
                uploaded_by
            ) VALUES(
                #{id},
                #{taskId},
                #{attemptNo},
                #{logicalSlot},
                #{assignmentStatus},
                #{fileName},
                #{contentType},
                #{sizeBytes},
                #{storedPath},
                #{checksum},
                #{uploadedBy}
            )
            """)
    int insert(TaskAttachment attachment);

    @Select("""
            SELECT id,
                   task_id,
                   attempt_no,
                   logical_slot,
                   assignment_status,
                   file_name,
                   content_type,
                   size_bytes,
                   stored_path,
                   checksum,
                   uploaded_by,
                   created_at
            FROM task_attachment
            WHERE task_id = #{taskId}
            ORDER BY created_at DESC
            """)
    List<TaskAttachment> findByTaskId(@Param("taskId") String taskId);

    @Select("""
            SELECT id,
                   task_id,
                   attempt_no,
                   logical_slot,
                   assignment_status,
                   file_name,
                   content_type,
                   size_bytes,
                   stored_path,
                   checksum,
                   uploaded_by,
                   created_at
            FROM task_attachment
            WHERE task_id = #{taskId}
              AND id = ANY(#{ids}::text[])
            """)
    List<TaskAttachment> findByTaskIdAndIds(@Param("taskId") String taskId, @Param("ids") String[] ids);
}

