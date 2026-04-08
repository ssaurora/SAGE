package com.sage.backend.audit;

import com.sage.backend.mapper.AuditRecordMapper;
import com.sage.backend.model.AuditRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private final AuditRecordMapper auditRecordMapper;

    public AuditService(AuditRecordMapper auditRecordMapper) {
        this.auditRecordMapper = auditRecordMapper;
    }

    public void appendAudit(String taskId, String actionType, String actionResult, String traceId, String detailJson) {
        AuditRecord auditRecord = new AuditRecord();
        auditRecord.setTaskId(taskId);
        auditRecord.setActionType(actionType);
        auditRecord.setActionResult(actionResult);
        auditRecord.setTraceId(traceId);
        auditRecord.setDetailJson(detailJson);
        auditRecordMapper.insert(auditRecord);
    }

    public List<AuditRecord> findByTaskId(String taskId) {
        return auditRecordMapper.findByTaskId(taskId);
    }
}
