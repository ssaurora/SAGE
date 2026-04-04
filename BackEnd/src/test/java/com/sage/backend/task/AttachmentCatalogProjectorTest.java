package com.sage.backend.task;

import com.sage.backend.model.TaskAttachment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachmentCatalogProjectorTest {

    @Test
    void buildCatalogSummaryCountsReadyAndBlacklistedAssets() {
        TaskAttachment precipitation = attachment(
                "att_precip",
                "precipitation",
                "precipitation.tif",
                "image/tiff",
                "/workspace/input/precipitation.tif",
                "sha256:precip",
                "ASSIGNED"
        );
        TaskAttachment blacklistedLulc = attachment(
                "att_lulc",
                "lulc",
                "lulc.tif",
                "image/tiff",
                "/workspace/input/lulc.tif",
                "sha256:lulc",
                "BLACKLISTED"
        );
        TaskAttachment incomplete = attachment(
                "att_missing",
                "eto",
                "eto.tif",
                "image/tiff",
                "",
                "sha256:eto",
                "ASSIGNED"
        );

        Map<String, Object> summary = AttachmentCatalogProjector.buildCatalogSummary(
                List.of(precipitation, blacklistedLulc, incomplete),
                7
        );

        assertEquals(3, summary.get("catalog_asset_count"));
        assertEquals(1, summary.get("catalog_ready_asset_count"));
        assertEquals(1, summary.get("catalog_blacklisted_asset_count"));
        assertEquals(1, summary.get("catalog_role_coverage_count"));
        assertEquals(List.of("precipitation"), summary.get("catalog_ready_role_names"));
        assertEquals(7, summary.get("catalog_revision"));
        assertEquals(64, summary.get("catalog_fingerprint").toString().length());
        assertEquals("task_attachment_projection", summary.get("catalog_source"));
    }

    @Test
    void buildCatalogSummaryUsesNoneSourceWhenAttachmentsMissing() {
        Map<String, Object> summary = AttachmentCatalogProjector.buildCatalogSummary(List.of());

        assertEquals(0, summary.get("catalog_asset_count"));
        assertEquals(0, summary.get("catalog_ready_asset_count"));
        assertEquals(0, summary.get("catalog_blacklisted_asset_count"));
        assertEquals(0, summary.get("catalog_role_coverage_count"));
        assertEquals(List.of(), summary.get("catalog_ready_role_names"));
        assertEquals(0, summary.get("catalog_revision"));
        assertEquals(64, summary.get("catalog_fingerprint").toString().length());
        assertEquals("none", summary.get("catalog_source"));
    }

    private TaskAttachment attachment(
            String id,
            String logicalSlot,
            String fileName,
            String contentType,
            String storedPath,
            String checksum,
            String assignmentStatus
    ) {
        TaskAttachment attachment = new TaskAttachment();
        attachment.setId(id);
        attachment.setLogicalSlot(logicalSlot);
        attachment.setFileName(fileName);
        attachment.setContentType(contentType);
        attachment.setStoredPath(storedPath);
        attachment.setChecksum(checksum);
        attachment.setAssignmentStatus(assignmentStatus);
        attachment.setSizeBytes(1024L);
        return attachment;
    }
}
