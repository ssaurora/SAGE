package com.sage.backend.mapper;

import com.sage.backend.model.TaskCatalogSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TaskCatalogSnapshotMapper {

    @Insert("""
            INSERT INTO task_catalog_snapshot(
                task_id,
                inventory_version,
                catalog_revision,
                catalog_fingerprint,
                catalog_summary_json,
                catalog_facts_json,
                catalog_source
            )
            VALUES(
                #{taskId},
                #{inventoryVersion},
                #{catalogRevision},
                #{catalogFingerprint},
                #{catalogSummaryJson},
                #{catalogFactsJson},
                #{catalogSource}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TaskCatalogSnapshot snapshot);

    @Select("""
            SELECT id,
                   task_id,
                   inventory_version,
                   catalog_revision,
                   catalog_fingerprint,
                   catalog_summary_json,
                   catalog_facts_json,
                   catalog_source,
                   created_at
            FROM task_catalog_snapshot
            WHERE task_id = #{taskId}
              AND inventory_version = #{inventoryVersion}
            """)
    TaskCatalogSnapshot findByTaskIdAndInventoryVersion(
            @Param("taskId") String taskId,
            @Param("inventoryVersion") int inventoryVersion
    );

    @Select("""
            SELECT id,
                   task_id,
                   inventory_version,
                   catalog_revision,
                   catalog_fingerprint,
                   catalog_summary_json,
                   catalog_facts_json,
                   catalog_source,
                   created_at
            FROM task_catalog_snapshot
            WHERE task_id = #{taskId}
            ORDER BY inventory_version DESC, id DESC
            LIMIT 1
            """)
    TaskCatalogSnapshot findLatestByTaskId(@Param("taskId") String taskId);
}
