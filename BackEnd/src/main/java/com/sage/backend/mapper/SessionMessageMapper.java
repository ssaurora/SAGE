package com.sage.backend.mapper;

import com.sage.backend.model.SessionMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SessionMessageMapper {

    @Insert("""
            INSERT INTO session_message(
                message_id,
                session_id,
                task_id,
                result_bundle_id,
                role,
                message_type,
                content_json,
                attachment_refs_json,
                action_schema_json,
                related_object_refs_json,
                created_at
            )
            VALUES(
                #{messageId},
                #{sessionId},
                #{taskId},
                #{resultBundleId},
                #{role},
                #{messageType},
                #{contentJson},
                #{attachmentRefsJson},
                #{actionSchemaJson},
                #{relatedObjectRefsJson},
                COALESCE(#{createdAt}, NOW())
            )
            """)
    int insert(SessionMessage message);

    @Select("""
            SELECT message_id,
                   session_id,
                   task_id,
                   result_bundle_id,
                   role,
                   message_type,
                   content_json,
                   attachment_refs_json,
                   action_schema_json,
                   related_object_refs_json,
                   created_at
            FROM session_message
            WHERE session_id = #{sessionId}
            ORDER BY created_at ASC, message_id ASC
            """)
    List<SessionMessage> findBySessionId(@Param("sessionId") String sessionId);

    @Delete("""
            DELETE FROM session_message
            WHERE session_id = #{sessionId}
            """)
    int deleteBySessionId(@Param("sessionId") String sessionId);
}
