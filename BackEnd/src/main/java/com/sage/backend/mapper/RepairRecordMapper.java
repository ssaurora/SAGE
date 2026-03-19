package com.sage.backend.mapper;

import com.sage.backend.model.RepairRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RepairRecordMapper {

    @Insert("""
            INSERT INTO repair_record(
                task_id,
                attempt_no,
                resume_request_id,
                dispatcher_output_json,
                repair_proposal_json,
                resume_payload_json,
                result
            ) VALUES(
                #{taskId},
                #{attemptNo},
                #{resumeRequestId},
                #{dispatcherOutputJson},
                #{repairProposalJson},
                #{resumePayloadJson},
                #{result}
            )
            """)
    int insert(RepairRecord repairRecord);

    @Select("""
            SELECT id,
                   task_id,
                   attempt_no,
                   resume_request_id,
                   dispatcher_output_json,
                   repair_proposal_json,
                   resume_payload_json,
                   result,
                   created_at
            FROM repair_record
            WHERE task_id = #{taskId}
              AND resume_request_id = #{resumeRequestId}
            LIMIT 1
            """)
    RepairRecord findByTaskIdAndResumeRequestId(
            @Param("taskId") String taskId,
            @Param("resumeRequestId") String resumeRequestId
    );

    @Select("""
            SELECT id,
                   task_id,
                   attempt_no,
                   resume_request_id,
                   dispatcher_output_json,
                   repair_proposal_json,
                   resume_payload_json,
                   result,
                   created_at
            FROM repair_record
            WHERE task_id = #{taskId}
              AND attempt_no = #{attemptNo}
            ORDER BY created_at DESC
            LIMIT 1
            """)
    RepairRecord findLatestByTaskIdAndAttemptNo(
            @Param("taskId") String taskId,
            @Param("attemptNo") int attemptNo
    );
}
