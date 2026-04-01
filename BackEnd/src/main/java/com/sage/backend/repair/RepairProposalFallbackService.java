package com.sage.backend.repair;

import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.repair.dto.RepairProposalResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RepairProposalFallbackService {

    public RepairProposalResponse generate(RepairProposalRequest request, String note) {
        RepairProposalRequest.ValidationSummary validationSummary = request == null ? null : request.getValidationSummary();
        RepairProposalRequest.FailureSummary failureSummary = request == null ? null : request.getFailureSummary();

        RepairProposalResponse proposal = new RepairProposalResponse();
        proposal.setAvailable(false);
        proposal.setUserFacingReason(null);
        proposal.setResumeHint(null);
        proposal.setActionExplanations(List.of());

        List<String> notes = new ArrayList<>();
        notes.add(note);
        notes.add("Dispatcher rules remain the source of truth.");
        String failureCode = "COGNITION_UNAVAILABLE";
        String failureMessage = "Repair proposal cognition unavailable.";
        String errorCode = validationSummary == null ? "" : validationSummary.getErrorCode();
        if (errorCode != null && !errorCode.isBlank()) {
            notes.add("Latest structured failure code: " + errorCode);
        } else {
            String latestFailureCode = failureSummary == null ? "" : failureSummary.getFailureCode();
            if (latestFailureCode != null && !latestFailureCode.isBlank()) {
                notes.add("Latest structured failure code: " + latestFailureCode);
            }
        }
        proposal.setNotes(notes);
        proposal.setFailureCode(failureCode);
        proposal.setFailureMessage(failureMessage);
        proposal.setCognitionMetadata(buildCognitionMetadata(failureCode, failureMessage));
        return proposal;
    }

    private Map<String, Object> buildCognitionMetadata(String failureCode, String failureMessage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "backend_fallback_unavailable");
        metadata.put("provider", "glm");
        metadata.put("model", null);
        metadata.put("prompt_version", "repair_proposal_v1");
        metadata.put("fallback_used", false);
        metadata.put("schema_valid", false);
        metadata.put("response_id", null);
        metadata.put("status", "COGNITION_UNAVAILABLE");
        metadata.put("failure_code", failureCode);
        metadata.put("failure_message", failureMessage);
        return metadata;
    }
}
