package com.sage.backend.mapper;

import com.sage.backend.model.AnalysisSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AnalysisSessionMapper {

    @Insert("""
            INSERT INTO analysis_session(
                session_id,
                user_id,
                title,
                user_goal,
                status,
                scene_id,
                current_task_id,
                latest_result_bundle_id,
                current_required_user_action_json,
                session_summary_json,
                created_at,
                updated_at
            )
            VALUES(
                #{sessionId},
                #{userId},
                #{title},
                #{userGoal},
                #{status},
                #{sceneId},
                #{currentTaskId},
                #{latestResultBundleId},
                #{currentRequiredUserActionJson},
                #{sessionSummaryJson},
                COALESCE(#{createdAt}, NOW()),
                COALESCE(#{updatedAt}, NOW())
            )
            """)
    int insert(AnalysisSession session);

    @Select("""
            SELECT session_id,
                   user_id,
                   title,
                   user_goal,
                   status,
                   scene_id,
                   current_task_id,
                   latest_result_bundle_id,
                   current_required_user_action_json,
                   session_summary_json,
                   created_at,
                   updated_at
            FROM analysis_session
            WHERE session_id = #{sessionId}
            """)
    AnalysisSession findBySessionId(@Param("sessionId") String sessionId);

    @Select("""
            SELECT session_id,
                   user_id,
                   title,
                   user_goal,
                   status,
                   scene_id,
                   current_task_id,
                   latest_result_bundle_id,
                   current_required_user_action_json,
                   session_summary_json,
                   created_at,
                   updated_at
            FROM analysis_session
            WHERE user_id = #{userId}
              AND (#{status} IS NULL OR status = #{status})
              AND (#{sceneId} IS NULL OR scene_id = #{sceneId})
            ORDER BY updated_at DESC, created_at DESC
            LIMIT #{limit}
            """)
    List<AnalysisSession> findByUserId(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("sceneId") String sceneId,
            @Param("limit") int limit
    );

    @Select("""
            SELECT session_id,
                   user_id,
                   title,
                   user_goal,
                   status,
                   scene_id,
                   current_task_id,
                   latest_result_bundle_id,
                   current_required_user_action_json,
                   session_summary_json,
                   created_at,
                   updated_at
            FROM analysis_session
            WHERE user_id = #{userId}
              AND scene_id IS NOT NULL
              AND scene_id <> ''
            ORDER BY updated_at DESC, created_at DESC, session_id ASC
            """)
    List<AnalysisSession> findSceneLinkedByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT session_id,
                   user_id,
                   title,
                   user_goal,
                   status,
                   scene_id,
                   current_task_id,
                   latest_result_bundle_id,
                   current_required_user_action_json,
                   session_summary_json,
                   created_at,
                   updated_at
            FROM analysis_session
            WHERE user_id = #{userId}
              AND scene_id = #{sceneId}
            ORDER BY updated_at DESC, created_at DESC, session_id ASC
            """)
    List<AnalysisSession> findByUserIdAndSceneIdAll(
            @Param("userId") Long userId,
            @Param("sceneId") String sceneId
    );

    @Update("""
            UPDATE analysis_session
            SET current_task_id = #{currentTaskId},
                latest_result_bundle_id = #{latestResultBundleId},
                status = #{status},
                current_required_user_action_json = #{currentRequiredUserActionJson},
                session_summary_json = #{sessionSummaryJson},
                updated_at = NOW()
            WHERE session_id = #{sessionId}
            """)
    int updateStateAndPointers(
            @Param("sessionId") String sessionId,
            @Param("currentTaskId") String currentTaskId,
            @Param("latestResultBundleId") String latestResultBundleId,
            @Param("status") String status,
            @Param("currentRequiredUserActionJson") String currentRequiredUserActionJson,
            @Param("sessionSummaryJson") String sessionSummaryJson
    );
}
