ALTER TABLE analysis_manifest
    ADD COLUMN IF NOT EXISTS catalog_summary_json TEXT;
