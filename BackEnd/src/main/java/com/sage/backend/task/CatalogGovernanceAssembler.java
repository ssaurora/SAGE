package com.sage.backend.task;

import com.sage.backend.task.dto.CatalogGovernanceView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CatalogGovernanceAssembler {

    private CatalogGovernanceAssembler() {
    }

    static CatalogGovernanceView build(
            String scope,
            Map<String, Object> baselineCatalogSummary,
            Map<String, Object> currentCatalogSummary,
            Map<String, Object> catalogConsistency
    ) {
        CatalogGovernanceView view = new CatalogGovernanceView();
        view.setScope(scope);
        view.setBaselineCatalogSummary(toCatalogIdentityView(baselineCatalogSummary));
        view.setCurrentCatalogSummary(toCatalogIdentityView(currentCatalogSummary));
        view.setConsistency(toCatalogConsistencyView(catalogConsistency));
        return view;
    }

    static CatalogGovernanceView buildAudit(Map<String, Object> detail, Map<String, Object> currentCatalogSummary) {
        Map<String, Object> auditCatalogSummary = resolveAuditCatalogSummary(detail);
        Map<String, Object> consistency = CatalogConsistencyProjector.buildFrozenCatalogConsistency(
                "audit_catalog",
                auditCatalogSummary,
                currentCatalogSummary
        );
        return build("audit_catalog_governance", auditCatalogSummary, currentCatalogSummary, consistency);
    }

    static boolean hasAuditCatalogEvidence(Map<String, Object> detail) {
        return !resolveAuditCatalogSummary(detail).isEmpty();
    }

    private static Map<String, Object> resolveAuditCatalogSummary(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> catalogSummary = detail.get("catalog_summary") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        if (!catalogSummary.isEmpty()) {
            return catalogSummary;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> catalogIdentity = detail.get("catalog_identity") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        if (!catalogIdentity.isEmpty()) {
            return catalogIdentity;
        }
        Map<String, Object> candidateCatalogSummary = catalogSummaryFromPrefixedFields(detail, "candidate");
        if (!candidateCatalogSummary.isEmpty()) {
            return candidateCatalogSummary;
        }
        return catalogSummaryFromPrefixedFields(detail, "base");
    }

    private static Map<String, Object> catalogSummaryFromPrefixedFields(Map<String, Object> detail, String prefix) {
        String prefixWithSeparator = prefix + "_";
        Object inventoryVersion = detail.get(prefixWithSeparator + "catalog_inventory_version");
        Object revision = detail.get(prefixWithSeparator + "catalog_revision");
        Object fingerprint = detail.get(prefixWithSeparator + "catalog_fingerprint");
        if (inventoryVersion == null && revision == null && fingerprint == null) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("catalog_source", "audit_" + prefix + "_catalog_identity");
        summary.put("catalog_inventory_version", inventoryVersion);
        summary.put("catalog_revision", revision);
        summary.put("catalog_fingerprint", fingerprint);
        summary.put("catalog_asset_count", 0);
        summary.put("catalog_ready_asset_count", 0);
        summary.put("catalog_blacklisted_asset_count", 0);
        summary.put("catalog_role_coverage_count", 0);
        summary.put("catalog_ready_role_names", List.of());
        return summary;
    }

    private static CatalogGovernanceView.CatalogIdentityView toCatalogIdentityView(Map<String, Object> summary) {
        CatalogGovernanceView.CatalogIdentityView view = new CatalogGovernanceView.CatalogIdentityView();
        if (summary == null || summary.isEmpty()) {
            view.setCatalogReadyRoleNames(List.of());
            view.setCatalogPresent(false);
            return view;
        }
        view.setCatalogSource(objectStringValue(summary.get("catalog_source")));
        view.setCatalogInventoryVersion(toInteger(summary.get("catalog_inventory_version")));
        view.setCatalogRevision(toInteger(summary.get("catalog_revision")));
        view.setCatalogFingerprint(objectStringValue(summary.get("catalog_fingerprint")));
        view.setCatalogAssetCount(toInteger(summary.get("catalog_asset_count")));
        view.setCatalogReadyAssetCount(toInteger(summary.get("catalog_ready_asset_count")));
        view.setCatalogBlacklistedAssetCount(toInteger(summary.get("catalog_blacklisted_asset_count")));
        view.setCatalogRoleCoverageCount(toInteger(summary.get("catalog_role_coverage_count")));
        @SuppressWarnings("unchecked")
        List<String> readyRoleNames = summary.get("catalog_ready_role_names") instanceof List<?> list ? (List<String>) list : List.of();
        view.setCatalogReadyRoleNames(readyRoleNames);
        view.setCatalogPresent(true);
        return view;
    }

    private static CatalogGovernanceView.CatalogConsistencyView toCatalogConsistencyView(Map<String, Object> consistency) {
        CatalogGovernanceView.CatalogConsistencyView view = new CatalogGovernanceView.CatalogConsistencyView();
        if (consistency == null || consistency.isEmpty()) {
            view.setCatalogReadyRoleNames(List.of());
            view.setExpectedRoleNames(List.of());
            view.setMissingCatalogRoles(List.of());
            view.setStaleMissingSlots(List.of());
            return view;
        }
        view.setScope(objectStringValue(consistency.get("scope")));
        view.setBaselineCatalogPresent((Boolean) consistency.get("baseline_catalog_present"));
        view.setCurrentCatalogPresent((Boolean) consistency.get("current_catalog_present"));
        view.setBaselineCatalogSource(objectStringValue(consistency.get("baseline_catalog_source")));
        view.setBaselineCatalogInventoryVersion(toInteger(consistency.get("baseline_catalog_inventory_version")));
        view.setBaselineCatalogRevision(toInteger(consistency.get("baseline_catalog_revision")));
        view.setBaselineCatalogFingerprint(objectStringValue(consistency.get("baseline_catalog_fingerprint")));
        view.setCurrentCatalogSource(objectStringValue(consistency.get("current_catalog_source")));
        view.setCurrentCatalogInventoryVersion(toInteger(consistency.get("current_catalog_inventory_version")));
        view.setCurrentCatalogRevision(toInteger(consistency.get("current_catalog_revision")));
        view.setCurrentCatalogFingerprint(objectStringValue(consistency.get("current_catalog_fingerprint")));
        view.setMatchesCurrentCatalog((Boolean) consistency.get("matches_current_catalog"));
        view.setDriftDetected((Boolean) consistency.get("drift_detected"));
        view.setMismatchCode(objectStringValue(consistency.get("mismatch_code")));
        view.setConsistencyCode(objectStringValue(consistency.get("consistency_code")));
        @SuppressWarnings("unchecked")
        List<String> expectedRoleNames = consistency.get("expected_role_names") instanceof List<?> list ? (List<String>) list : List.of();
        @SuppressWarnings("unchecked")
        List<String> readyRoleNames = consistency.get("catalog_ready_role_names") instanceof List<?> list ? (List<String>) list : List.of();
        @SuppressWarnings("unchecked")
        List<String> missingCatalogRoles = consistency.get("missing_catalog_roles") instanceof List<?> list ? (List<String>) list : List.of();
        @SuppressWarnings("unchecked")
        List<String> staleMissingSlots = consistency.get("stale_missing_slots") instanceof List<?> list ? (List<String>) list : List.of();
        view.setExpectedRoleNames(expectedRoleNames);
        view.setCatalogReadyRoleNames(readyRoleNames);
        view.setMissingCatalogRoles(missingCatalogRoles);
        view.setCovered((Boolean) consistency.get("covered"));
        view.setStaleMissingSlots(staleMissingSlots);
        return view;
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String objectStringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
