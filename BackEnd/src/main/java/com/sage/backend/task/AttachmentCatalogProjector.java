package com.sage.backend.task;

import com.sage.backend.model.TaskAttachment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class AttachmentCatalogProjector {

    private AttachmentCatalogProjector() {
    }

    static List<Map<String, Object>> project(List<TaskAttachment> attachments) {
        List<Map<String, Object>> projected = new ArrayList<>();
        if (attachments == null) {
            return projected;
        }
        for (TaskAttachment attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("asset_id", safeString(attachment.getId()));
            fact.put("logical_role_candidates", logicalRoleCandidates(attachment));
            fact.put("file_type", inferFileType(attachment));
            fact.put("crs", null);
            fact.put("extent", null);
            fact.put("resolution", null);
            fact.put("nodata_info", null);
            fact.put("source", "task_attachment");
            fact.put("checksum_version", safeString(attachment.getChecksum()));
            fact.put("availability_status", isCatalogReady(attachment) ? "READY" : "MISSING_METADATA");
            fact.put("blacklist_flag", isBlacklisted(attachment));
            projected.add(fact);
        }
        return projected;
    }

    public static Map<String, Object> buildCatalogSummary(List<TaskAttachment> attachments) {
        return buildCatalogSummary(attachments, null);
    }

    public static Map<String, Object> buildCatalogSummary(List<TaskAttachment> attachments, Integer catalogRevision) {
        List<Map<String, Object>> facts = project(attachments);
        int readyAssetCount = 0;
        int blacklistedAssetCount = 0;
        Set<String> readyRoleNames = new HashSet<>();
        for (Map<String, Object> fact : facts) {
            if (fact == null) {
                continue;
            }
            if (Boolean.TRUE.equals(fact.get("blacklist_flag"))) {
                blacklistedAssetCount += 1;
                continue;
            }
            if (!"READY".equalsIgnoreCase(safeString(stringValue(fact.get("availability_status"))))) {
                continue;
            }
            readyAssetCount += 1;
            Object roleCandidates = fact.get("logical_role_candidates");
            if (roleCandidates instanceof List<?> candidateList) {
                for (Object candidate : candidateList) {
                    String roleName = safeString(candidate == null ? null : candidate.toString());
                    if (!roleName.isBlank()) {
                        readyRoleNames.add(roleName);
                    }
                }
            }
        }
        List<String> sortedReadyRoleNames = new ArrayList<>(readyRoleNames);
        Collections.sort(sortedReadyRoleNames);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("catalog_asset_count", facts.size());
        summary.put("catalog_ready_asset_count", readyAssetCount);
        summary.put("catalog_blacklisted_asset_count", blacklistedAssetCount);
        summary.put("catalog_role_coverage_count", readyRoleNames.size());
        summary.put("catalog_ready_role_names", sortedReadyRoleNames);
        summary.put("catalog_revision", catalogRevision == null ? 0 : catalogRevision);
        summary.put("catalog_fingerprint", buildCatalogFingerprint(facts, catalogRevision));
        summary.put("catalog_source", facts.isEmpty() ? "none" : "task_attachment_projection");
        return summary;
    }

    public static Set<String> resolveReadyRoleNames(List<TaskAttachment> attachments) {
        Set<String> readyRoleNames = new HashSet<>();
        for (Map<String, Object> fact : project(attachments)) {
            if (fact == null || Boolean.TRUE.equals(fact.get("blacklist_flag"))) {
                continue;
            }
            if (!"READY".equalsIgnoreCase(safeString(stringValue(fact.get("availability_status"))))) {
                continue;
            }
            Object roleCandidates = fact.get("logical_role_candidates");
            if (!(roleCandidates instanceof List<?> candidateList)) {
                continue;
            }
            for (Object candidate : candidateList) {
                String roleName = safeString(candidate == null ? null : candidate.toString());
                if (!roleName.isBlank()) {
                    readyRoleNames.add(roleName);
                }
            }
        }
        return readyRoleNames;
    }

    public static List<String> extractReadyRoleNames(Map<String, Object> catalogSummary) {
        if (catalogSummary == null) {
            return List.of();
        }
        Object value = catalogSummary.get("catalog_ready_role_names");
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Object item : listValue) {
            String roleName = safeString(item == null ? null : item.toString());
            if (!roleName.isBlank()) {
                names.add(roleName);
            }
        }
        Collections.sort(names);
        return names;
    }

    public static Map<String, Object> buildCoverageConsistency(List<String> expectedRoleNames, Map<String, Object> catalogSummary, String scope) {
        List<String> readyRoleNames = extractReadyRoleNames(catalogSummary);
        Set<String> readyRoleSet = new HashSet<>(readyRoleNames);
        Set<String> expectedSet = new TreeSet<>();
        if (expectedRoleNames != null) {
            for (String roleName : expectedRoleNames) {
                String normalized = safeString(roleName);
                if (!normalized.isBlank()) {
                    expectedSet.add(normalized);
                }
            }
        }
        List<String> missingCatalogRoles = new ArrayList<>();
        for (String roleName : expectedSet) {
            if (!readyRoleSet.contains(roleName)) {
                missingCatalogRoles.add(roleName);
            }
        }
        Map<String, Object> consistency = new LinkedHashMap<>();
        consistency.put("scope", safeString(scope));
        consistency.put("catalog_source", catalogSummary == null ? "" : safeString(stringValue(catalogSummary.get("catalog_source"))));
        consistency.put("catalog_revision", extractCatalogRevision(catalogSummary));
        consistency.put("catalog_fingerprint", extractCatalogFingerprint(catalogSummary));
        consistency.put("catalog_ready_role_names", readyRoleNames);
        consistency.put("expected_role_names", new ArrayList<>(expectedSet));
        consistency.put("missing_catalog_roles", missingCatalogRoles);
        consistency.put("covered", missingCatalogRoles.isEmpty());
        return consistency;
    }

    public static Integer extractCatalogRevision(Map<String, Object> catalogSummary) {
        if (catalogSummary == null) {
            return null;
        }
        Object value = catalogSummary.get("catalog_revision");
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
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

    public static String extractCatalogFingerprint(Map<String, Object> catalogSummary) {
        if (catalogSummary == null) {
            return "";
        }
        return safeString(stringValue(catalogSummary.get("catalog_fingerprint")));
    }

    public static boolean sameCatalogIdentity(Map<String, Object> left, Map<String, Object> right) {
        Integer leftRevision = extractCatalogRevision(left);
        Integer rightRevision = extractCatalogRevision(right);
        String leftFingerprint = extractCatalogFingerprint(left);
        String rightFingerprint = extractCatalogFingerprint(right);
        if (leftRevision != null && rightRevision != null && !leftFingerprint.isBlank() && !rightFingerprint.isBlank()) {
            return leftRevision.equals(rightRevision) && leftFingerprint.equals(rightFingerprint);
        }
        return left != null && left.equals(right);
    }

    static boolean isCatalogReady(TaskAttachment attachment) {
        return attachment != null
                && !safeString(attachment.getId()).isBlank()
                && !safeString(attachment.getLogicalSlot()).isBlank()
                && !safeString(attachment.getFileName()).isBlank()
                && attachment.getSizeBytes() != null
                && attachment.getSizeBytes() >= 0
                && !safeString(attachment.getStoredPath()).isBlank()
                && !safeString(attachment.getChecksum()).isBlank();
    }

    private static boolean isBlacklisted(TaskAttachment attachment) {
        return "BLACKLISTED".equalsIgnoreCase(safeString(attachment == null ? null : attachment.getAssignmentStatus()));
    }

    private static List<String> logicalRoleCandidates(TaskAttachment attachment) {
        String slot = safeString(attachment == null ? null : attachment.getLogicalSlot());
        if (slot.isBlank()) {
            return List.of();
        }
        return List.of(slot);
    }

    private static String inferFileType(TaskAttachment attachment) {
        String contentType = safeString(attachment == null ? null : attachment.getContentType());
        if (!contentType.isBlank()) {
            return contentType;
        }
        String fileName = safeString(attachment == null ? null : attachment.getFileName()).toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".tif") || fileName.endsWith(".tiff")) {
            return "raster";
        }
        if (fileName.endsWith(".shp")) {
            return "vector";
        }
        if (fileName.endsWith(".csv")) {
            return "table";
        }
        return null;
    }

    private static String buildCatalogFingerprint(List<Map<String, Object>> facts, Integer catalogRevision) {
        List<String> entries = new ArrayList<>();
        for (Map<String, Object> fact : facts) {
            if (fact == null) {
                continue;
            }
            List<String> roles = new ArrayList<>();
            Object roleCandidates = fact.get("logical_role_candidates");
            if (roleCandidates instanceof List<?> candidateList) {
                for (Object candidate : candidateList) {
                    String roleName = safeString(candidate == null ? null : candidate.toString());
                    if (!roleName.isBlank()) {
                        roles.add(roleName);
                    }
                }
            }
            Collections.sort(roles);
            entries.add(String.join("|",
                    safeString(stringValue(fact.get("asset_id"))),
                    String.join(",", roles),
                    safeString(stringValue(fact.get("file_type"))),
                    safeString(stringValue(fact.get("checksum_version"))),
                    safeString(stringValue(fact.get("availability_status"))),
                    String.valueOf(Boolean.TRUE.equals(fact.get("blacklist_flag")))));
        }
        Collections.sort(entries);
        String canonical = "catalog_revision=" + (catalogRevision == null ? 0 : catalogRevision) + "\n" + String.join("\n", entries);
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(safeString(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to compute catalog fingerprint", exception);
        }
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
