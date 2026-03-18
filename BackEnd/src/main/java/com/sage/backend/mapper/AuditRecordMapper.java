package com.sage.backend.mapper;

import com.sage.backend.model.AuditRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditRecordMapper {

    @Insert("""
            INSERT INTO audit_record(task_id, action_type, action_result, trace_id, detail_json)
            VALUES(#{taskId}, #{actionType}, #{actionResult}, #{traceId}, #{detailJson})
            """)
    int insert(AuditRecord auditRecord);
}

