package com.sage.backend.task;

import com.sage.backend.task.dto.TaskDetailResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CatalogConsistencyProjector {

    private CatalogConsistencyProjector() {
    }

    static Map<String, Object> buildFrozenCatalogConsistency(
            String scope,
            Map<String, Object> baselineCatalogSummary,
            Map<String, Object> currentCatalogSummary
    ) {
        Map<String, Object> consistency = new LinkedHashMap<>();
        boolean baselinePresent = baselineCatalogSummary != null && !baselineCatalogSummary.isEmpty();
        boolean currentPresent = currentCatalogSummary != null && !currentCatalogSummary.isEmpty();
        Integer baselineInventoryVersion = AttachmentCatalogProjector.extractCatalogInventoryVersion(baselineCatalogSummary);
        Integer baselineRevision = AttachmentCatalogProjector.extractCatalogRevision(baselineCatalogSummary);
        String baselineFingerprint = AttachmentCatalogProjector.extractCatalogFingerprint(baselineCatalogSummary);
        Integer currentInventoryVersion = AttachmentCatalogProjector.extractCatalogInventoryVersion(currentCatalogSummary);
        Integer currentRevision = AttachmentCatalogProjector.extractCatalogRevision(currentCatalogSummary);
        String currentFingerprint = AttachmentCatalogProjector.extractCatalogFingerprint(currentCatalogSummary);
        boolean matches = baselinePresent && currentPresent && AttachmentCatalogProjector.sameCatalogIdentity(baselineCatalogSummary, currentCatalogSummary);
        String mismatchCode = determineCatalogMismatchCode(
                baselinePresent,
                currentPresent,
                baselineRevision,
                baselineFingerprint,
                currentRevision,
                currentFingerprint
        );
        consistency.put("scope", safeString(scope));
        consistency.put("baseline_catalog_present", baselinePresent);
        consistency.put("current_catalog_present", currentPresent);
        consistency.put("baseline_catalog_source", baselinePresent ? safeString(stringValue(baselineCatalogSummary.get("catalog_source"))) : "");
        consistency.put("baseline_catalog_inventory_version", baselineInventoryVersion);
        consistency.put("baseline_catalog_revision", baselineRevision);
        consistency.put("baseline_catalog_fingerprint", baselineFingerprint);
        consistency.put("current_catalog_source", currentPresent ? safeString(stringValue(currentCatalogSummary.get("catalog_source"))) : "");
        consistency.put("current_catalog_inventory_version", currentInventoryVersion);
        consistency.put("current_catalog_revision", currentRevision);
        consistency.put("current_catalog_fingerprint", currentFingerprint);
        consistency.put("matches_current_catalog", matches);
        consistency.put("drift_detected", baselinePresent && currentPresent && !matches);
        consistency.put("mismatch_code", mismatchCode);
        consistency.put("consistency_code", matches ? "CATALOG_MATCHED" : mismatchCode);
        return consistency;
    }

    static Map<String, Object> buildDetailCatalogConsistency(
            TaskDetailResponse.WaitingContext waitingContext,
            Map<String, Object> currentCatalogSummary
    ) {
        Map<String, Object> waitingCatalogSummary = waitingContext == null ? null : waitingContext.getCatalogSummary();
        Map<String, Object> consistency = buildFrozenCatalogConsistency(
                "waiting_context_catalog",
                waitingCatalogSummary,
                currentCatalogSummary
        );
        consistency.put("stale_missing_slots", resolveStaleWaitingSlots(waitingContext, currentCatalogSummary));
        return consistency;
    }

    static Map<String, Object> mergeCoverageConsistency(
            Map<String, Object> identityConsistency,
            List<String> expectedRoleNames,
            Map<String, Object> currentCatalogSummary,
            String scope
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (identityConsistency != null && !identityConsistency.isEmpty()) {
            merged.putAll(identityConsistency);
        }
        Map<String, Object> coverage = AttachmentCatalogProjector.buildCoverageConsistency(expectedRoleNames, currentCatalogSummary, scope);
        merged.put("scope", safeString(scope));
        merged.put("expected_role_names", coverage.get("expected_role_names"));
        merged.put("catalog_ready_role_names", coverage.get("catalog_ready_role_names"));
        merged.put("missing_catalog_roles", coverage.get("missing_catalog_roles"));
        merged.put("covered", coverage.get("covered"));
        return merged;
    }

    static String determineCatalogMismatchCode(
            boolean baselinePresent,
            boolean currentPresent,
            Integer baselineRevision,
            String baselineFingerprint,
            Integer currentRevision,
            String currentFingerprint
    ) {
        if (!baselinePresent && !currentPresent) {
            return "CATALOG_IDENTITY_UNAVAILABLE";
        }
        if (!baselinePresent) {
            return "BASELINE_CATALOG_MISSING";
        }
        if (!currentPresent) {
            return "CURRENT_CATALOG_MISSING";
        }
        if (!Objects.equals(baselineRevision, currentRevision)) {
            return "CATALOG_REVISION_MISMATCH";
        }
        if (!Objects.equals(safeString(baselineFingerprint), safeString(currentFingerprint))) {
            return "CATALOG_FINGERPRINT_MISMATCH";
        }
        return null;
    }

    private static List<String> resolveStaleWaitingSlots(
            TaskDetailResponse.WaitingContext waitingContext,
            Map<String, Object> currentCatalogSummary
    ) {
        if (waitingContext == null || waitingContext.getMissingSlots() == null || waitingContext.getMissingSlots().isEmpty()) {
            return List.of();
        }
        Set<String> readyRoleNames = new LinkedHashSet<>(AttachmentCatalogProjector.extractReadyRoleNames(currentCatalogSummary));
        List<String> staleSlots = new ArrayList<>();
        for (TaskDetailResponse.MissingSlot missingSlot : waitingContext.getMissingSlots()) {
            String slotName = missingSlot == null ? null : missingSlot.getSlotName();
            if (slotName != null && readyRoleNames.contains(slotName)) {
                staleSlots.add(slotName);
            }
        }
        return staleSlots;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
