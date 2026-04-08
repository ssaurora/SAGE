CREATE TABLE IF NOT EXISTS task_catalog_snapshot (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL REFERENCES task_state(task_id),
    inventory_version INTEGER NOT NULL CHECK (inventory_version >= 0),
    catalog_revision INTEGER NOT NULL CHECK (catalog_revision >= 0),
    catalog_fingerprint VARCHAR(128) NOT NULL,
    catalog_summary_json TEXT NOT NULL,
    catalog_facts_json TEXT NOT NULL,
    catalog_source VARCHAR(64) NOT NULL DEFAULT 'task_attachment_projection',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(task_id, inventory_version)
);

CREATE INDEX IF NOT EXISTS idx_task_catalog_snapshot_task_inventory
    ON task_catalog_snapshot (task_id, inventory_version DESC);
