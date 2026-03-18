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
            INSERT INTO task_state(task_id, user_id, current_state, state_version, user_query, pass1_result_json)
            VALUES(#{taskId}, #{userId}, #{currentState}, #{stateVersion}, #{userQuery}, #{pass1ResultJson})
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

    @Select("""
            SELECT task_id, user_id, current_state, state_version, user_query, pass1_result_json, created_at, updated_at
            FROM task_state
            WHERE task_id = #{taskId}
            """)
    TaskState findByTaskId(@Param("taskId") String taskId);
}

