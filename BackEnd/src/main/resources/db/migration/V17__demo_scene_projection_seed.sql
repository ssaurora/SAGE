DO $$
DECLARE
    demo_user_id BIGINT;
BEGIN
    SELECT id INTO demo_user_id
    FROM app_user
    WHERE username = 'demo';

    IF demo_user_id IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO analysis_session (
        session_id,
        user_id,
        title,
        user_goal,
        status,
        scene_id,
        current_task_id,
        latest_result_bundle_id,
        current_required_user_action_json,
        session_summary_json,
        created_at,
        updated_at
    )
    VALUES
        (
            'sess_demo_delta_coastline',
            demo_user_id,
            'Delta Coastline Protection Planning',
            'Assess how mangroves and salt marsh restoration can reduce coastal flood exposure along the delta shoreline.',
            'WAITING_USER',
            'scene-delta-coastline-protection-planning',
            'task_demo_delta_coastline',
            NULL,
            jsonb_build_object(
                'waiting_reason_type', 'MISSING_REQUIRED_INPUT',
                'missing_slots', jsonb_build_array(
                    jsonb_build_object('slot_name', 'shoreline_habitat_map', 'expected_type', 'file/tif', 'required', true),
                    jsonb_build_object('slot_name', 'coastal_exposure_assets', 'expected_type', 'file/gpkg', 'required', true)
                ),
                'invalid_bindings', jsonb_build_array(),
                'required_user_actions', jsonb_build_array(
                    jsonb_build_object('action_type', 'UPLOAD', 'key', 'shoreline_habitat_map', 'label', 'Upload shoreline habitat raster', 'required', true),
                    jsonb_build_object('action_type', 'UPLOAD', 'key', 'coastal_exposure_assets', 'label', 'Upload exposure assets layer', 'required', true)
                ),
                'resume_hint', 'Upload the habitat raster and exposure assets to continue coastal protection planning.',
                'can_resume', true,
                'catalog_summary', NULL,
                'why_blocked', 'MISSING_REQUIRED_INPUT',
                'user_facing_phrasing', 'The system is blocked on required coastal habitat and exposure inputs before governed execution can continue.'
            )::text,
            jsonb_build_object(
                'task_id', 'task_demo_delta_coastline',
                'status', 'WAITING_USER',
                'user_goal', 'Assess how mangroves and salt marsh restoration can reduce coastal flood exposure along the delta shoreline.',
                'latest_result_bundle_id', NULL
            )::text,
            TIMESTAMPTZ '2026-04-13T08:10:00Z',
            TIMESTAMPTZ '2026-04-13T08:35:00Z'
        ),
        (
            'sess_demo_upper_watershed',
            demo_user_id,
            'Upper Watershed Sediment Retention',
            'Estimate sediment retention performance in the upper watershed and identify high-leverage erosion control subcatchments.',
            'RUNNING',
            'scene-upper-watershed-sediment-retention',
            'task_demo_upper_watershed',
            NULL,
            NULL,
            jsonb_build_object(
                'task_id', 'task_demo_upper_watershed',
                'status', 'RUNNING',
                'user_goal', 'Estimate sediment retention performance in the upper watershed and identify high-leverage erosion control subcatchments.',
                'latest_result_bundle_id', NULL
            )::text,
            TIMESTAMPTZ '2026-04-13T07:40:00Z',
            TIMESTAMPTZ '2026-04-13T09:00:00Z'
        ),
        (
            'sess_demo_urban_cooling',
            demo_user_id,
            'Urban Cooling for Heat Mitigation',
            'Identify urban cooling priority zones where tree canopy and park expansion would most reduce extreme heat exposure.',
            'READY_RESULT',
            'scene-urban-cooling-for-heat-mitigation',
            'task_demo_urban_cooling',
            'rb_demo_urban_cooling_2024',
            NULL,
            jsonb_build_object(
                'task_id', 'task_demo_urban_cooling',
                'status', 'READY_RESULT',
                'user_goal', 'Identify urban cooling priority zones where tree canopy and park expansion would most reduce extreme heat exposure.',
                'latest_result_bundle_id', 'rb_demo_urban_cooling_2024'
            )::text,
            TIMESTAMPTZ '2026-04-12T05:50:00Z',
            TIMESTAMPTZ '2026-04-12T18:40:10Z'
        ),
        (
            'sess_demo_pollinator_orchard',
            demo_user_id,
            'Pollinator Habitat Support for Orchard Belt',
            'Evaluate pollinator habitat sufficiency and movement support across the orchard belt to guide habitat restoration placement.',
            'FAILED',
            'scene-pollinator-habitat-support-for-orchard-belt',
            'task_demo_pollinator_orchard',
            NULL,
            NULL,
            jsonb_build_object(
                'task_id', 'task_demo_pollinator_orchard',
                'status', 'FAILED',
                'user_goal', 'Evaluate pollinator habitat sufficiency and movement support across the orchard belt to guide habitat restoration placement.',
                'latest_result_bundle_id', NULL
            )::text,
            TIMESTAMPTZ '2026-04-11T06:25:00Z',
            TIMESTAMPTZ '2026-04-13T06:15:00Z'
        ),
        (
            'sess_demo_restoration_carbon',
            demo_user_id,
            'Restoration Carbon Baseline 2024',
            'Review the archived 2024 carbon storage baseline for the restoration portfolio and compare it with the new planning cohort.',
            'CANCELLED',
            'scene-restoration-carbon-baseline-2024',
            'task_demo_restoration_carbon',
            NULL,
            NULL,
            jsonb_build_object(
                'task_id', 'task_demo_restoration_carbon',
                'status', 'CANCELLED',
                'user_goal', 'Review the archived 2024 carbon storage baseline for the restoration portfolio and compare it with the new planning cohort.',
                'latest_result_bundle_id', NULL
            )::text,
            TIMESTAMPTZ '2026-04-10T04:20:00Z',
            TIMESTAMPTZ '2026-04-10T16:30:00Z'
        ),
        (
            'sess_demo_corridor_habitat',
            demo_user_id,
            'Development Corridor Habitat Quality Screening',
            'Screen habitat quality risk along the planned development corridor and confirm the authoritative analysis boundary before execution resumes.',
            'WAITING_USER',
            'scene-development-corridor-habitat-quality-screening',
            'task_demo_corridor_habitat',
            NULL,
            jsonb_build_object(
                'waiting_reason_type', 'INVALID_BINDING',
                'missing_slots', jsonb_build_array(),
                'invalid_bindings', jsonb_build_array('corridor_alignment', 'analysis_aoi'),
                'required_user_actions', jsonb_build_array(
                    jsonb_build_object('action_type', 'CONFIRM_BINDING', 'key', 'corridor_alignment', 'label', 'Confirm corridor alignment binding', 'required', true),
                    jsonb_build_object('action_type', 'CONFIRM_SCOPE', 'key', 'analysis_aoi', 'label', 'Confirm analysis scope boundary', 'required', true)
                ),
                'resume_hint', 'Confirm the corridor alignment and the analysis boundary to continue habitat quality screening.',
                'can_resume', true,
                'catalog_summary', NULL,
                'why_blocked', 'INVALID_BINDING',
                'user_facing_phrasing', 'The system is blocked on binding and scope confirmation before governed execution can continue.'
            )::text,
            jsonb_build_object(
                'task_id', 'task_demo_corridor_habitat',
                'status', 'WAITING_USER',
                'user_goal', 'Screen habitat quality risk along the planned development corridor and confirm the authoritative analysis boundary before execution resumes.',
                'latest_result_bundle_id', NULL
            )::text,
            TIMESTAMPTZ '2026-04-13T08:40:00Z',
            TIMESTAMPTZ '2026-04-13T08:50:00Z'
        )
    ON CONFLICT (session_id) DO NOTHING;

    INSERT INTO task_state (
        task_id,
        user_id,
        current_state,
        state_version,
        user_query,
        created_at,
        updated_at,
        waiting_context_json,
        waiting_reason_type,
        waiting_since,
        active_attempt_no,
        planning_revision,
        checkpoint_version,
        cognition_verdict,
        latest_result_bundle_id,
        latest_workspace_id,
        result_bundle_summary_json,
        final_explanation_summary_json,
        last_failure_summary_json,
        session_id,
        job_id
    )
    VALUES
        (
            'task_demo_delta_coastline',
            demo_user_id,
            'WAITING_USER',
            12,
            'Assess how mangroves and salt marsh restoration can reduce coastal flood exposure along the delta shoreline.',
            TIMESTAMPTZ '2026-04-13T08:10:00Z',
            TIMESTAMPTZ '2026-04-13T08:35:00Z',
            jsonb_build_object(
                'waiting_reason_type', 'MISSING_REQUIRED_INPUT',
                'missing_slots', jsonb_build_array(
                    jsonb_build_object('slot_name', 'shoreline_habitat_map', 'expected_type', 'file/tif', 'required', true),
                    jsonb_build_object('slot_name', 'coastal_exposure_assets', 'expected_type', 'file/gpkg', 'required', true)
                ),
                'invalid_bindings', jsonb_build_array(),
                'required_user_actions', jsonb_build_array(
                    jsonb_build_object('action_type', 'UPLOAD', 'key', 'shoreline_habitat_map', 'label', 'Upload shoreline habitat raster', 'required', true),
                    jsonb_build_object('action_type', 'UPLOAD', 'key', 'coastal_exposure_assets', 'label', 'Upload exposure assets layer', 'required', true)
                ),
                'resume_hint', 'Upload the habitat raster and exposure assets to continue coastal protection planning.',
                'can_resume', true,
                'catalog_summary', NULL
            )::text,
            'MISSING_REQUIRED_INPUT',
            TIMESTAMPTZ '2026-04-13T08:34:00Z',
            1,
            3,
            1,
            'PASS',
            NULL,
            NULL,
            NULL,
            NULL,
            NULL,
            'sess_demo_delta_coastline',
            NULL
        ),
        (
            'task_demo_upper_watershed',
            demo_user_id,
            'RUNNING',
            18,
            'Estimate sediment retention performance in the upper watershed and identify high-leverage erosion control subcatchments.',
            TIMESTAMPTZ '2026-04-13T07:40:00Z',
            TIMESTAMPTZ '2026-04-13T09:00:00Z',
            NULL,
            NULL,
            NULL,
            1,
            5,
            2,
            'PASS',
            NULL,
            'ws_demo_upper_watershed_1',
            NULL,
            NULL,
            NULL,
            'sess_demo_upper_watershed',
            'job_demo_upper_watershed_1'
        ),
        (
            'task_demo_urban_cooling',
            demo_user_id,
            'SUCCEEDED',
            24,
            'Identify urban cooling priority zones where tree canopy and park expansion would most reduce extreme heat exposure.',
            TIMESTAMPTZ '2026-04-12T05:50:00Z',
            TIMESTAMPTZ '2026-04-12T18:40:10Z',
            NULL,
            NULL,
            NULL,
            1,
            6,
            3,
            'PASS',
            'rb_demo_urban_cooling_2024',
            'ws_demo_urban_cooling_1',
            jsonb_build_object(
                'result_id', 'rb_demo_urban_cooling_2024',
                'summary', 'Cooling priority zones, canopy opportunity blocks, and exposure-weighted beneficiary estimates are ready.',
                'main_output_count', 2,
                'main_outputs', jsonb_build_array('cooling_priority_zones.gpkg', 'canopy_expansion_priority.tif'),
                'primary_outputs', jsonb_build_array('cooling_priority_zones.gpkg'),
                'audit_artifacts', jsonb_build_array('urban_cooling_method_note.md'),
                'created_at', '2026-04-12T18:40:10Z'
            )::text,
            jsonb_build_object(
                'title', 'Urban cooling hotspots and canopy expansion priorities are ready',
                'highlight_count', 3,
                'generated_at', '2026-04-12T18:41:00Z',
                'available', true
            )::text,
            NULL,
            'sess_demo_urban_cooling',
            'job_demo_urban_cooling_1'
        ),
        (
            'task_demo_pollinator_orchard',
            demo_user_id,
            'FAILED',
            16,
            'Evaluate pollinator habitat sufficiency and movement support across the orchard belt to guide habitat restoration placement.',
            TIMESTAMPTZ '2026-04-11T06:25:00Z',
            TIMESTAMPTZ '2026-04-13T06:15:00Z',
            NULL,
            NULL,
            NULL,
            1,
            4,
            2,
            'FAIL',
            NULL,
            NULL,
            NULL,
            NULL,
            jsonb_build_object(
                'failure_code', 'POLLINATOR_CONNECTIVITY_GRAPH_FAILED',
                'failure_message', 'The orchard belt connectivity graph could not be constructed because habitat polygons contain unresolved topology gaps.',
                'created_at', '2026-04-13T06:15:00Z',
                'repairable', false,
                'details', jsonb_build_object('failed_stage', 'graph_assembly')
            )::text,
            'sess_demo_pollinator_orchard',
            NULL
        ),
        (
            'task_demo_restoration_carbon',
            demo_user_id,
            'CANCELLED',
            9,
            'Review the archived 2024 carbon storage baseline for the restoration portfolio and compare it with the new planning cohort.',
            TIMESTAMPTZ '2026-04-10T04:20:00Z',
            TIMESTAMPTZ '2026-04-10T16:30:00Z',
            NULL,
            NULL,
            NULL,
            1,
            2,
            1,
            'PASS',
            NULL,
            NULL,
            NULL,
            NULL,
            NULL,
            'sess_demo_restoration_carbon',
            NULL
        ),
        (
            'task_demo_corridor_habitat',
            demo_user_id,
            'WAITING_USER',
            11,
            'Screen habitat quality risk along the planned development corridor and confirm the authoritative analysis boundary before execution resumes.',
            TIMESTAMPTZ '2026-04-13T08:40:00Z',
            TIMESTAMPTZ '2026-04-13T08:50:00Z',
            jsonb_build_object(
                'waiting_reason_type', 'INVALID_BINDING',
                'missing_slots', jsonb_build_array(),
                'invalid_bindings', jsonb_build_array('corridor_alignment', 'analysis_aoi'),
                'required_user_actions', jsonb_build_array(
                    jsonb_build_object('action_type', 'CONFIRM_BINDING', 'key', 'corridor_alignment', 'label', 'Confirm corridor alignment binding', 'required', true),
                    jsonb_build_object('action_type', 'CONFIRM_SCOPE', 'key', 'analysis_aoi', 'label', 'Confirm analysis scope boundary', 'required', true)
                ),
                'resume_hint', 'Confirm the corridor alignment and the analysis boundary to continue habitat quality screening.',
                'can_resume', true,
                'catalog_summary', NULL
            )::text,
            'INVALID_BINDING',
            TIMESTAMPTZ '2026-04-13T08:49:00Z',
            1,
            3,
            1,
            'PASS',
            NULL,
            NULL,
            NULL,
            NULL,
            NULL,
            'sess_demo_corridor_habitat',
            NULL
        )
    ON CONFLICT (task_id) DO NOTHING;

    UPDATE task_state
    SET pass1_result_json = jsonb_build_object(
            'capability_facts', jsonb_build_object(
                'capability_key', 'water_yield',
                'display_name', 'Water Yield',
                'contract_version', 'demo-v1',
                'contract_fingerprint', 'demo-water-yield-contract-v1',
                'contracts', jsonb_build_object(
                    'validate_bindings', jsonb_build_object(
                        'caller_scope', 'control_or_planning',
                        'side_effect_level', 'read_only',
                        'input_schema', 'schema://demo/validate-bindings-input',
                        'output_schema', 'schema://demo/validate-bindings-output'
                    ),
                    'validate_args', jsonb_build_object(
                        'caller_scope', 'control_or_planning',
                        'side_effect_level', 'read_only',
                        'input_schema', 'schema://demo/validate-args-input',
                        'output_schema', 'schema://demo/validate-args-output'
                    ),
                    'submit_job', jsonb_build_object(
                        'caller_scope', 'control_only',
                        'side_effect_level', 'runtime_submission',
                        'input_schema', 'schema://demo/submit-job-input',
                        'output_schema', 'schema://demo/submit-job-output'
                    ),
                    'query_job_status', jsonb_build_object(
                        'caller_scope', 'control_or_presentation',
                        'side_effect_level', 'read_only',
                        'input_schema', 'schema://demo/query-job-status-input',
                        'output_schema', 'schema://demo/query-job-status-output'
                    ),
                    'collect_result_bundle', jsonb_build_object(
                        'caller_scope', 'control_only',
                        'side_effect_level', 'artifact_collection',
                        'input_schema', 'schema://demo/collect-result-input',
                        'output_schema', 'schema://demo/collect-result-output'
                    ),
                    'index_artifacts', jsonb_build_object(
                        'caller_scope', 'control_only',
                        'side_effect_level', 'artifact_indexing',
                        'input_schema', 'schema://demo/index-artifacts-input',
                        'output_schema', 'schema://demo/index-artifacts-output'
                    ),
                    'record_audit', jsonb_build_object(
                        'caller_scope', 'control_only',
                        'side_effect_level', 'audit_write',
                        'input_schema', 'schema://demo/record-audit-input',
                        'output_schema', 'schema://demo/record-audit-output'
                    )
                )
            )
        )::text
    WHERE task_id = 'task_demo_upper_watershed';

    INSERT INTO job_record (
        job_id,
        task_id,
        attempt_no,
        job_state,
        workspace_id,
        provider_key,
        capability_key,
        runtime_profile,
        result_bundle_json,
        final_explanation_json,
        failure_summary_json,
        workspace_summary_json,
        artifact_catalog_json,
        accepted_at,
        started_at,
        finished_at,
        last_heartbeat_at,
        created_at,
        updated_at
    )
    VALUES
        (
            'job_demo_upper_watershed_1',
            'task_demo_upper_watershed',
            1,
            'RUNNING',
            'ws_demo_upper_watershed_1',
            'planning-pass1-local',
            'sediment_retention',
            'docker-local',
            NULL,
            NULL,
            NULL,
            jsonb_build_object(
                'workspace_id', 'ws_demo_upper_watershed_1',
                'workspace_output_path', 'runtime/workspaces/ws_demo_upper_watershed_1/output',
                'archive_path', NULL,
                'cleanup_completed', false,
                'archive_completed', false
            )::text,
            jsonb_build_object(
                'primary_outputs', jsonb_build_array(),
                'intermediate_outputs', jsonb_build_array(),
                'audit_artifacts', jsonb_build_array(),
                'derived_outputs', jsonb_build_array(),
                'logs', jsonb_build_array(
                    jsonb_build_object(
                        'artifact_id', 'artifact_demo_upper_watershed_log',
                        'artifact_role', 'LOG',
                        'logical_name', 'run_log',
                        'relative_path', 'logs/sediment_retention.log',
                        'absolute_path', NULL,
                        'content_type', 'text/plain',
                        'size_bytes', 2048,
                        'sha256', 'demo-upper-watershed-log',
                        'created_at', '2026-04-13T08:59:00Z'
                    )
                )
            )::text,
            TIMESTAMPTZ '2026-04-13T07:45:00Z',
            TIMESTAMPTZ '2026-04-13T07:47:00Z',
            NULL,
            TIMESTAMPTZ '2026-04-13T09:00:00Z',
            TIMESTAMPTZ '2026-04-13T07:45:00Z',
            TIMESTAMPTZ '2026-04-13T09:00:00Z'
        ),
        (
            'job_demo_urban_cooling_1',
            'task_demo_urban_cooling',
            1,
            'SUCCEEDED',
            'ws_demo_urban_cooling_1',
            'planning-pass1-local',
            'urban_cooling',
            'docker-local',
            jsonb_build_object(
                'result_id', 'rb_demo_urban_cooling_2024',
                'task_id', 'task_demo_urban_cooling',
                'job_id', 'job_demo_urban_cooling_1',
                'summary', 'Cooling priority zones, canopy opportunity blocks, and exposure-weighted beneficiary estimates are ready.',
                'metrics', jsonb_build_object(
                    'high_priority_blocks', 18,
                    'beneficiaries_exposed', 245000,
                    'mean_cooling_improvement_c', 1.7
                ),
                'output_registry', jsonb_build_object(
                    'priority_zones', 'cooling_priority_zones.gpkg',
                    'canopy_raster', 'canopy_expansion_priority.tif'
                ),
                'primary_output_refs', jsonb_build_array(
                    jsonb_build_object('output_id', 'priority_zones', 'path', 'outputs/cooling_priority_zones.gpkg'),
                    jsonb_build_object('output_id', 'canopy_raster', 'path', 'outputs/canopy_expansion_priority.tif')
                ),
                'main_outputs', jsonb_build_array('cooling_priority_zones.gpkg', 'canopy_expansion_priority.tif'),
                'artifacts', jsonb_build_array('cooling_priority_zones.gpkg', 'canopy_expansion_priority.tif', 'urban_cooling_method_note.md'),
                'primary_outputs', jsonb_build_array('cooling_priority_zones.gpkg'),
                'intermediate_outputs', jsonb_build_array('heat_vulnerability_grid.tif'),
                'audit_artifacts', jsonb_build_array('urban_cooling_method_note.md'),
                'derived_outputs', jsonb_build_array('beneficiary_summary.csv'),
                'created_at', '2026-04-12T18:40:10Z'
            )::text,
            jsonb_build_object(
                'available', true,
                'title', 'Urban cooling hotspots and canopy expansion priorities are ready',
                'highlights', jsonb_build_array(
                    '18 high-priority cooling opportunity blocks were identified.',
                    'Priority zones overlap neighborhoods with high elderly heat exposure.',
                    'Canopy expansion in the top quartile zones yields the strongest modeled cooling gains.'
                ),
                'narrative', 'The modeled cooling surface indicates that targeted canopy expansion and park enhancement would reduce extreme heat exposure most effectively in the eastern urban growth belt.',
                'generated_at', '2026-04-12T18:41:00Z'
            )::text,
            NULL,
            jsonb_build_object(
                'workspace_id', 'ws_demo_urban_cooling_1',
                'workspace_output_path', 'runtime/workspaces/ws_demo_urban_cooling_1/output',
                'archive_path', 'runtime/workspaces/ws_demo_urban_cooling_1/archive.tar.gz',
                'cleanup_completed', true,
                'archive_completed', true
            )::text,
            jsonb_build_object(
                'primary_outputs', jsonb_build_array(
                    jsonb_build_object(
                        'artifact_id', 'artifact_demo_urban_priority_zones',
                        'artifact_role', 'PRIMARY_OUTPUT',
                        'logical_name', 'priority_zones',
                        'relative_path', 'outputs/cooling_priority_zones.gpkg',
                        'absolute_path', NULL,
                        'content_type', 'application/geopackage+sqlite3',
                        'size_bytes', 184320,
                        'sha256', 'demo-urban-priority-zones',
                        'created_at', '2026-04-12T18:40:10Z'
                    )
                ),
                'intermediate_outputs', jsonb_build_array(
                    jsonb_build_object(
                        'artifact_id', 'artifact_demo_urban_heat_grid',
                        'artifact_role', 'INTERMEDIATE_OUTPUT',
                        'logical_name', 'heat_vulnerability_grid',
                        'relative_path', 'intermediate/heat_vulnerability_grid.tif',
                        'absolute_path', NULL,
                        'content_type', 'image/tiff',
                        'size_bytes', 823552,
                        'sha256', 'demo-urban-heat-grid',
                        'created_at', '2026-04-12T18:39:00Z'
                    )
                ),
                'audit_artifacts', jsonb_build_array(
                    jsonb_build_object(
                        'artifact_id', 'artifact_demo_urban_method_note',
                        'artifact_role', 'AUDIT_ARTIFACT',
                        'logical_name', 'method_note',
                        'relative_path', 'audit/urban_cooling_method_note.md',
                        'absolute_path', NULL,
                        'content_type', 'text/markdown',
                        'size_bytes', 4096,
                        'sha256', 'demo-urban-method-note',
                        'created_at', '2026-04-12T18:40:20Z'
                    )
                ),
                'derived_outputs', jsonb_build_array(
                    jsonb_build_object(
                        'artifact_id', 'artifact_demo_urban_beneficiaries',
                        'artifact_role', 'DERIVED_OUTPUT',
                        'logical_name', 'beneficiary_summary',
                        'relative_path', 'derived/beneficiary_summary.csv',
                        'absolute_path', NULL,
                        'content_type', 'text/csv',
                        'size_bytes', 4096,
                        'sha256', 'demo-urban-beneficiaries',
                        'created_at', '2026-04-12T18:40:25Z'
                    )
                ),
                'logs', jsonb_build_array(
                    jsonb_build_object(
                        'artifact_id', 'artifact_demo_urban_log',
                        'artifact_role', 'LOG',
                        'logical_name', 'run_log',
                        'relative_path', 'logs/urban_cooling.log',
                        'absolute_path', NULL,
                        'content_type', 'text/plain',
                        'size_bytes', 10240,
                        'sha256', 'demo-urban-log',
                        'created_at', '2026-04-12T18:40:30Z'
                    )
                )
            )::text,
            TIMESTAMPTZ '2026-04-12T05:55:00Z',
            TIMESTAMPTZ '2026-04-12T05:57:00Z',
            TIMESTAMPTZ '2026-04-12T18:40:10Z',
            TIMESTAMPTZ '2026-04-12T18:40:10Z',
            TIMESTAMPTZ '2026-04-12T05:55:00Z',
            TIMESTAMPTZ '2026-04-12T18:40:10Z'
        )
    ON CONFLICT (job_id) DO NOTHING;

    INSERT INTO workspace_registry (
        workspace_id,
        task_id,
        job_id,
        attempt_no,
        runtime_profile,
        container_name,
        host_workspace_path,
        archive_path,
        workspace_state,
        created_at,
        started_at,
        finished_at,
        archived_at,
        updated_at
    )
    VALUES
        (
            'ws_demo_upper_watershed_1',
            'task_demo_upper_watershed',
            'job_demo_upper_watershed_1',
            1,
            'docker-local',
            'sage-upper-watershed-runner',
            'runtime/workspaces/ws_demo_upper_watershed_1',
            NULL,
            'RUNNING',
            TIMESTAMPTZ '2026-04-13T07:45:00Z',
            TIMESTAMPTZ '2026-04-13T07:47:00Z',
            NULL,
            NULL,
            TIMESTAMPTZ '2026-04-13T09:00:00Z'
        ),
        (
            'ws_demo_urban_cooling_1',
            'task_demo_urban_cooling',
            'job_demo_urban_cooling_1',
            1,
            'docker-local',
            'sage-urban-cooling-runner',
            'runtime/workspaces/ws_demo_urban_cooling_1',
            'runtime/workspaces/ws_demo_urban_cooling_1/archive.tar.gz',
            'ARCHIVED',
            TIMESTAMPTZ '2026-04-12T05:55:00Z',
            TIMESTAMPTZ '2026-04-12T05:57:00Z',
            TIMESTAMPTZ '2026-04-12T18:40:10Z',
            TIMESTAMPTZ '2026-04-12T18:42:00Z',
            TIMESTAMPTZ '2026-04-12T18:42:00Z'
        )
    ON CONFLICT (workspace_id) DO NOTHING;

    INSERT INTO result_bundle_record (
        result_bundle_id,
        task_id,
        job_id,
        attempt_no,
        manifest_id,
        workspace_id,
        result_bundle_json,
        final_explanation_json,
        summary_text,
        created_at
    )
    VALUES (
        'rb_demo_urban_cooling_2024',
        'task_demo_urban_cooling',
        'job_demo_urban_cooling_1',
        1,
        NULL,
        'ws_demo_urban_cooling_1',
        jsonb_build_object(
            'result_id', 'rb_demo_urban_cooling_2024',
            'task_id', 'task_demo_urban_cooling',
            'job_id', 'job_demo_urban_cooling_1',
            'summary', 'Cooling priority zones, canopy opportunity blocks, and exposure-weighted beneficiary estimates are ready.',
            'metrics', jsonb_build_object(
                'high_priority_blocks', 18,
                'beneficiaries_exposed', 245000,
                'mean_cooling_improvement_c', 1.7
            ),
            'output_registry', jsonb_build_object(
                'priority_zones', 'cooling_priority_zones.gpkg',
                'canopy_raster', 'canopy_expansion_priority.tif'
            ),
            'primary_output_refs', jsonb_build_array(
                jsonb_build_object('output_id', 'priority_zones', 'path', 'outputs/cooling_priority_zones.gpkg'),
                jsonb_build_object('output_id', 'canopy_raster', 'path', 'outputs/canopy_expansion_priority.tif')
            ),
            'main_outputs', jsonb_build_array('cooling_priority_zones.gpkg', 'canopy_expansion_priority.tif'),
            'artifacts', jsonb_build_array('cooling_priority_zones.gpkg', 'canopy_expansion_priority.tif', 'urban_cooling_method_note.md'),
            'primary_outputs', jsonb_build_array('cooling_priority_zones.gpkg'),
            'intermediate_outputs', jsonb_build_array('heat_vulnerability_grid.tif'),
            'audit_artifacts', jsonb_build_array('urban_cooling_method_note.md'),
            'derived_outputs', jsonb_build_array('beneficiary_summary.csv'),
            'created_at', '2026-04-12T18:40:10Z'
        )::text,
        jsonb_build_object(
            'available', true,
            'title', 'Urban cooling hotspots and canopy expansion priorities are ready',
            'highlights', jsonb_build_array(
                '18 high-priority cooling opportunity blocks were identified.',
                'Priority zones overlap neighborhoods with high elderly heat exposure.',
                'Canopy expansion in the top quartile zones yields the strongest modeled cooling gains.'
            ),
            'narrative', 'The modeled cooling surface indicates that targeted canopy expansion and park enhancement would reduce extreme heat exposure most effectively in the eastern urban growth belt.',
            'generated_at', '2026-04-12T18:41:00Z'
        )::text,
        'Cooling priority zones, canopy opportunity blocks, and exposure-weighted beneficiary estimates are ready.',
        TIMESTAMPTZ '2026-04-12T18:40:10Z'
    )
    ON CONFLICT (result_bundle_id) DO NOTHING;

    INSERT INTO audit_record (
        task_id,
        action_type,
        action_result,
        trace_id,
        detail_json,
        created_at
    )
    VALUES
        (
            'task_demo_delta_coastline',
            'WAITING_CONTEXT_RECORDED',
            'SUCCESS',
            'trace_demo_delta_waiting',
            jsonb_build_object('reason', 'missing coastal habitat and exposure inputs')::text,
            TIMESTAMPTZ '2026-04-13T08:34:30Z'
        ),
        (
            'task_demo_upper_watershed',
            'JOB_DISPATCHED',
            'SUCCESS',
            'trace_demo_upper_running',
            jsonb_build_object('job_id', 'job_demo_upper_watershed_1')::text,
            TIMESTAMPTZ '2026-04-13T07:47:00Z'
        ),
        (
            'task_demo_urban_cooling',
            'RESULT_BUNDLE_REGISTERED',
            'SUCCESS',
            'trace_demo_urban_result',
            jsonb_build_object('result_bundle_id', 'rb_demo_urban_cooling_2024')::text,
            TIMESTAMPTZ '2026-04-12T18:40:10Z'
        ),
        (
            'task_demo_pollinator_orchard',
            'TASK_FAILED',
            'FAILED',
            'trace_demo_pollinator_failed',
            jsonb_build_object('failure_code', 'POLLINATOR_CONNECTIVITY_GRAPH_FAILED')::text,
            TIMESTAMPTZ '2026-04-13T06:15:00Z'
        ),
        (
            'task_demo_restoration_carbon',
            'SESSION_CANCELLED',
            'SUCCESS',
            'trace_demo_restoration_archived',
            jsonb_build_object('reason', 'superseded by 2026 planning cohort')::text,
            TIMESTAMPTZ '2026-04-10T16:30:00Z'
        ),
        (
            'task_demo_corridor_habitat',
            'WAITING_CONTEXT_RECORDED',
            'SUCCESS',
            'trace_demo_corridor_binding',
            jsonb_build_object('reason', 'invalid corridor alignment and analysis boundary bindings')::text,
            TIMESTAMPTZ '2026-04-13T08:49:30Z'
        );

    INSERT INTO event_log (
        task_id,
        event_type,
        from_state,
        to_state,
        state_version,
        event_payload_json,
        created_at
    )
    VALUES
        (
            'task_demo_delta_coastline',
            'STATE_CHANGED',
            'VALIDATING',
            'WAITING_USER',
            12,
            jsonb_build_object('waiting_reason_type', 'MISSING_REQUIRED_INPUT')::text,
            TIMESTAMPTZ '2026-04-13T08:34:00Z'
        ),
        (
            'task_demo_upper_watershed',
            'STATE_CHANGED',
            'QUEUED',
            'RUNNING',
            18,
            jsonb_build_object('job_id', 'job_demo_upper_watershed_1')::text,
            TIMESTAMPTZ '2026-04-13T07:47:00Z'
        ),
        (
            'task_demo_urban_cooling',
            'STATE_CHANGED',
            'RESULT_PROCESSING',
            'SUCCEEDED',
            24,
            jsonb_build_object('result_bundle_id', 'rb_demo_urban_cooling_2024')::text,
            TIMESTAMPTZ '2026-04-12T18:40:10Z'
        ),
        (
            'task_demo_pollinator_orchard',
            'TASK_FAILED',
            'RUNNING',
            'FAILED',
            16,
            jsonb_build_object('failure_code', 'POLLINATOR_CONNECTIVITY_GRAPH_FAILED')::text,
            TIMESTAMPTZ '2026-04-13T06:15:00Z'
        ),
        (
            'task_demo_restoration_carbon',
            'STATE_CHANGED',
            'RUNNING',
            'CANCELLED',
            9,
            jsonb_build_object('reason', 'superseded by updated baseline request')::text,
            TIMESTAMPTZ '2026-04-10T16:30:00Z'
        ),
        (
            'task_demo_corridor_habitat',
            'STATE_CHANGED',
            'VALIDATING',
            'WAITING_USER',
            11,
            jsonb_build_object('waiting_reason_type', 'INVALID_BINDING')::text,
            TIMESTAMPTZ '2026-04-13T08:49:00Z'
        );
END $$;
