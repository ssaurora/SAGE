INSERT INTO session_message (
    message_id,
    session_id,
    task_id,
    result_bundle_id,
    role,
    message_type,
    content_json,
    attachment_refs_json,
    action_schema_json,
    related_object_refs_json,
    created_at
)
SELECT *
FROM (
    VALUES
        (
            'msg_demo_delta_001',
            'sess_demo_delta_coastline',
            'task_demo_delta_coastline',
            NULL,
            'user',
            'user_goal',
            jsonb_build_object(
                'text', 'Assess how mangroves and salt marsh restoration can reduce coastal flood exposure along the delta shoreline.',
                'client_request_id', 'seed_demo_delta_goal'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_delta_coastline')::text,
            TIMESTAMPTZ '2026-04-13T08:10:00Z'
        ),
        (
            'msg_demo_delta_002',
            'sess_demo_delta_coastline',
            'task_demo_delta_coastline',
            NULL,
            'assistant',
            'assistant_understanding',
            jsonb_build_object(
                'text', 'I understood this as a coastline protection planning request focused on flood mitigation from mangroves and salt marshes along the delta shoreline.'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_delta_coastline')::text,
            TIMESTAMPTZ '2026-04-13T08:10:20Z'
        ),
        (
            'msg_demo_delta_003',
            'sess_demo_delta_coastline',
            'task_demo_delta_coastline',
            NULL,
            'assistant',
            'waiting_notice',
            jsonb_build_object(
                'text', 'I still need the shoreline habitat raster and the coastal exposure assets layer before governed execution can continue.',
                'waiting_reason_type', 'MISSING_REQUIRED_INPUT',
                'user_facing_phrasing', 'The system is blocked on required coastal habitat and exposure inputs before governed execution can continue.',
                'missing_slots', jsonb_build_array(
                    jsonb_build_object('slot_name', 'shoreline_habitat_map', 'expected_type', 'file/tif', 'required', true),
                    jsonb_build_object('slot_name', 'coastal_exposure_assets', 'expected_type', 'file/gpkg', 'required', true)
                ),
                'required_user_actions', jsonb_build_array(
                    jsonb_build_object('action_type', 'UPLOAD', 'key', 'shoreline_habitat_map', 'label', 'Upload shoreline habitat raster', 'required', true),
                    jsonb_build_object('action_type', 'UPLOAD', 'key', 'coastal_exposure_assets', 'label', 'Upload exposure assets layer', 'required', true)
                )
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_delta_coastline')::text,
            TIMESTAMPTZ '2026-04-13T08:35:00Z'
        ),
        (
            'msg_demo_upper_001',
            'sess_demo_upper_watershed',
            'task_demo_upper_watershed',
            NULL,
            'user',
            'user_goal',
            jsonb_build_object(
                'text', 'Estimate sediment retention performance in the upper watershed and identify high-leverage erosion control subcatchments.',
                'client_request_id', 'seed_demo_upper_goal'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_upper_watershed')::text,
            TIMESTAMPTZ '2026-04-13T07:40:00Z'
        ),
        (
            'msg_demo_upper_002',
            'sess_demo_upper_watershed',
            'task_demo_upper_watershed',
            NULL,
            'assistant',
            'assistant_understanding',
            jsonb_build_object(
                'text', 'I understood this as a sediment retention analysis focused on upper watershed erosion sources and downstream transport reduction leverage.'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_upper_watershed')::text,
            TIMESTAMPTZ '2026-04-13T07:40:18Z'
        ),
        (
            'msg_demo_upper_003',
            'sess_demo_upper_watershed',
            'task_demo_upper_watershed',
            NULL,
            'assistant',
            'progress_update',
            jsonb_build_object(
                'current_phase_label', 'RUNNING',
                'current_system_action', 'Executing sediment routing and retention aggregation',
                'latest_progress_note', 'Hydrologic preprocessing is complete and subcatchment retention coefficients are now being evaluated.',
                'estimated_next_milestone', 'Subcatchment ranking and delivery hotspot summary'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_upper_watershed')::text,
            TIMESTAMPTZ '2026-04-13T08:22:00Z'
        ),
        (
            'msg_demo_upper_004',
            'sess_demo_upper_watershed',
            'task_demo_upper_watershed',
            NULL,
            'system',
            'system_note',
            jsonb_build_object(
                'text', 'The current run is continuing under governed execution. No user intervention is required at this stage.'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_upper_watershed')::text,
            TIMESTAMPTZ '2026-04-13T09:00:00Z'
        ),
        (
            'msg_demo_urban_001',
            'sess_demo_urban_cooling',
            'task_demo_urban_cooling',
            NULL,
            'user',
            'user_goal',
            jsonb_build_object(
                'text', 'Identify urban cooling priority zones where tree canopy and park expansion would most reduce extreme heat exposure.',
                'client_request_id', 'seed_demo_urban_goal'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_urban_cooling')::text,
            TIMESTAMPTZ '2026-04-12T05:50:00Z'
        ),
        (
            'msg_demo_urban_002',
            'sess_demo_urban_cooling',
            'task_demo_urban_cooling',
            NULL,
            'assistant',
            'assistant_understanding',
            jsonb_build_object(
                'text', 'I understood this as an urban cooling prioritization request focused on canopy expansion, park access, and heat exposure reduction.'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_urban_cooling')::text,
            TIMESTAMPTZ '2026-04-12T05:50:15Z'
        ),
        (
            'msg_demo_urban_003',
            'sess_demo_urban_cooling',
            'task_demo_urban_cooling',
            NULL,
            'assistant',
            'progress_update',
            jsonb_build_object(
                'current_phase_label', 'RESULT_PROCESSING',
                'current_system_action', 'Assembling cooling priority surfaces and final explanation',
                'latest_progress_note', 'Tree canopy gap, park deficit, and vulnerable population overlays have been merged.',
                'estimated_next_milestone', 'Governed result bundle publication'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_urban_cooling')::text,
            TIMESTAMPTZ '2026-04-12T17:55:00Z'
        ),
        (
            'msg_demo_urban_004',
            'sess_demo_urban_cooling',
            'task_demo_urban_cooling',
            'rb_demo_urban_cooling_2024',
            'assistant',
            'result_summary',
            jsonb_build_object(
                'text', 'A governed result is ready. The highest-value cooling interventions cluster around low-canopy residential heat islands in the southwest and central transit corridor.',
                'summary', 'Cooling priority zones emphasize low-canopy neighborhoods with high afternoon heat burden and limited park access.',
                'highlights', jsonb_build_array(
                    'Southwest low-canopy residential blocks rank highest for canopy expansion.',
                    'Central transit corridor shows the largest cooling benefit per hectare of added shade.',
                    'Pocket park expansion is recommended near heat-vulnerable apartment clusters.'
                )
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_urban_cooling', 'result_bundle_id', 'rb_demo_urban_cooling_2024')::text,
            TIMESTAMPTZ '2026-04-12T18:40:10Z'
        ),
        (
            'msg_demo_urban_005',
            'sess_demo_urban_cooling',
            'task_demo_urban_cooling',
            'rb_demo_urban_cooling_2024',
            'assistant',
            'next_step_guidance',
            jsonb_build_object(
                'text', 'Review the cooling priority map, inspect governance details, and decide whether to continue with a corridor-specific follow-up request.'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_urban_cooling', 'result_bundle_id', 'rb_demo_urban_cooling_2024')::text,
            TIMESTAMPTZ '2026-04-12T18:40:20Z'
        ),
        (
            'msg_demo_pollinator_001',
            'sess_demo_pollinator_orchard',
            'task_demo_pollinator_orchard',
            NULL,
            'user',
            'user_goal',
            jsonb_build_object(
                'text', 'Evaluate pollinator habitat sufficiency and movement support across the orchard belt to guide habitat restoration placement.',
                'client_request_id', 'seed_demo_pollinator_goal'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_pollinator_orchard')::text,
            TIMESTAMPTZ '2026-04-11T06:25:00Z'
        ),
        (
            'msg_demo_pollinator_002',
            'sess_demo_pollinator_orchard',
            'task_demo_pollinator_orchard',
            NULL,
            'assistant',
            'assistant_understanding',
            jsonb_build_object(
                'text', 'I understood this as a pollinator habitat connectivity analysis for orchard restoration prioritization across the orchard belt.'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_pollinator_orchard')::text,
            TIMESTAMPTZ '2026-04-11T06:25:18Z'
        ),
        (
            'msg_demo_pollinator_003',
            'sess_demo_pollinator_orchard',
            'task_demo_pollinator_orchard',
            NULL,
            'assistant',
            'failure_explanation',
            jsonb_build_object(
                'text', 'Execution failed because orchard habitat polygons contain unresolved topology gaps, so the connectivity graph could not be constructed.',
                'failure_message', 'The orchard belt connectivity graph could not be constructed because habitat polygons contain unresolved topology gaps.'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_pollinator_orchard')::text,
            TIMESTAMPTZ '2026-04-13T06:15:00Z'
        ),
        (
            'msg_demo_restoration_001',
            'sess_demo_restoration_carbon',
            'task_demo_restoration_carbon',
            NULL,
            'user',
            'user_goal',
            jsonb_build_object(
                'text', 'Review the archived 2024 carbon storage baseline for the restoration portfolio and compare it with the new planning cohort.',
                'client_request_id', 'seed_demo_restoration_goal'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_restoration_carbon')::text,
            TIMESTAMPTZ '2026-04-10T04:20:00Z'
        ),
        (
            'msg_demo_restoration_002',
            'sess_demo_restoration_carbon',
            'task_demo_restoration_carbon',
            NULL,
            'assistant',
            'assistant_understanding',
            jsonb_build_object(
                'text', 'I understood this as a baseline carbon review request for the archived 2024 restoration portfolio.'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_restoration_carbon')::text,
            TIMESTAMPTZ '2026-04-10T04:20:16Z'
        ),
        (
            'msg_demo_restoration_003',
            'sess_demo_restoration_carbon',
            'task_demo_restoration_carbon',
            NULL,
            'system',
            'system_note',
            jsonb_build_object(
                'text', 'This session is archived. The 2024 baseline remains available for reference, but governed execution is no longer active.'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_restoration_carbon')::text,
            TIMESTAMPTZ '2026-04-10T16:30:00Z'
        ),
        (
            'msg_demo_corridor_001',
            'sess_demo_corridor_habitat',
            'task_demo_corridor_habitat',
            NULL,
            'user',
            'user_goal',
            jsonb_build_object(
                'text', 'Screen habitat quality risk along the planned development corridor and confirm the authoritative analysis boundary before execution resumes.',
                'client_request_id', 'seed_demo_corridor_goal'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_corridor_habitat')::text,
            TIMESTAMPTZ '2026-04-13T08:40:00Z'
        ),
        (
            'msg_demo_corridor_002',
            'sess_demo_corridor_habitat',
            'task_demo_corridor_habitat',
            NULL,
            'assistant',
            'assistant_understanding',
            jsonb_build_object(
                'text', 'I understood this as a habitat quality screening request for the planned development corridor, pending confirmation of the authoritative corridor alignment and analysis boundary.'
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_corridor_habitat')::text,
            TIMESTAMPTZ '2026-04-13T08:40:18Z'
        ),
        (
            'msg_demo_corridor_003',
            'sess_demo_corridor_habitat',
            'task_demo_corridor_habitat',
            NULL,
            'assistant',
            'clarification_request',
            jsonb_build_object(
                'text', 'Before I continue, confirm the corridor alignment binding and the analysis scope boundary.',
                'waiting_reason_type', 'INVALID_BINDING',
                'user_facing_phrasing', 'The system is blocked on binding and scope confirmation before governed execution can continue.',
                'invalid_bindings', jsonb_build_array('corridor_alignment', 'analysis_aoi'),
                'required_user_actions', jsonb_build_array(
                    jsonb_build_object('action_type', 'CONFIRM_BINDING', 'key', 'corridor_alignment', 'label', 'Confirm corridor alignment binding', 'required', true),
                    jsonb_build_object('action_type', 'CONFIRM_SCOPE', 'key', 'analysis_aoi', 'label', 'Confirm analysis scope boundary', 'required', true)
                )
            )::text,
            NULL,
            NULL,
            jsonb_build_object('task_id', 'task_demo_corridor_habitat')::text,
            TIMESTAMPTZ '2026-04-13T08:50:00Z'
        )
) AS seed(
    message_id,
    session_id,
    task_id,
    result_bundle_id,
    role,
    message_type,
    content_json,
    attachment_refs_json,
    action_schema_json,
    related_object_refs_json,
    created_at
)
WHERE EXISTS (
    SELECT 1
    FROM analysis_session session
    WHERE session.session_id = seed.session_id
)
ON CONFLICT (message_id) DO NOTHING;
