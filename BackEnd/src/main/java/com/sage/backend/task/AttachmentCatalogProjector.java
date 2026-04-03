package com.sage.backend.task;

import com.sage.backend.model.TaskAttachment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AttachmentCatalogProjector {

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

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
