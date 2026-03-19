package com.sage.backend.mapper;

import com.sage.backend.model.EventLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EventLogMapper {

    @Insert("""
            INSERT INTO event_log(task_id, event_type, from_state, to_state, state_version, event_payload_json, created_at)
            VALUES(#{taskId}, #{eventType}, #{fromState}, #{toState}, #{stateVersion}, #{eventPayloadJson}, clock_timestamp())
            """)
    int insert(EventLog eventLog);

    @Select("""
            SELECT id, task_id, event_type, from_state, to_state, state_version, event_payload_json, created_at
            FROM event_log
            WHERE task_id = #{taskId}
            ORDER BY created_at ASC, id ASC
            """)
    List<EventLog> findByTaskId(@Param("taskId") String taskId);
}
