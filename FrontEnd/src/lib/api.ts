import { fetchEventSource } from "@microsoft/fetch-event-source";

import { getAccessToken } from "@/lib/auth";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

type ApiOptions = RequestInit & {
  withAuth?: boolean;
};

async function apiFetch<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const headers = new Headers(options.headers ?? {});
  const isFormData = options.body instanceof FormData;
  if (!isFormData && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  if (options.withAuth) {
    const token = getAccessToken();
    if (!token) {
      throw new Error("未登录，请先登录");
    }
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error("认证失败，请重新登录");
    }
    const text = await response.text();
    throw new Error(text || `请求失败: ${response.status}`);
  }

  return (await response.json()) as T;
}

export type LoginResponse = {
  access_token: string;
  token_type: string;
  user: {
    user_id: string;
    username: string;
    role?: string;
  };
};

export function login(username: string, password: string): Promise<LoginResponse> {
  return apiFetch<LoginResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export type MeResponse = {
  user_id: string;
  username: string;
  role?: string;
};

export type ResumeTransaction = {
  resume_request_id?: string;
  status?: string;
  base_checkpoint_version?: number;
  candidate_checkpoint_version?: number;
  candidate_inventory_version?: number;
  candidate_manifest_id?: string;
  candidate_attempt_no?: number;
  candidate_job_id?: string;
  failure_reason?: string;
  updated_at?: string;
};

export type CorruptionState = {
  is_corrupted?: boolean;
  reason?: string;
  corrupted_since?: string;
};

export type CognitionMetadataDto = {
  provider?: string;
  model?: string | null;
  source?: string;
  prompt_version?: string;
  fallback_used?: boolean;
  response_id?: string | null;
  schema_valid?: boolean;
  status?: string;
  failure_code?: string | null;
  failure_message?: string | null;
};

export type CaseProjectionDto = {
  mode?: "resolved" | "clarify_required" | "unavailable" | string;
  selected_case_id?: string | null;
  candidate_case_ids?: string[];
  clarify_prompt?: string | null;
  decision_basis?: string[];
  registry_version?: string;
};

export function getMe(): Promise<MeResponse> {
  return apiFetch<MeResponse>("/auth/me", { method: "GET", withAuth: true });
}

export type CreateTaskResponse = {
  task_id: string;
  job_id?: string | null;
  state: string;
  state_version: number;
};

export function createTask(userQuery: string): Promise<CreateTaskResponse> {
  return apiFetch<CreateTaskResponse>("/tasks", {
    method: "POST",
    withAuth: true,
    body: JSON.stringify({ user_query: userQuery }),
  });
}

export type CapabilityValidationHintDto = {
  role_name?: string;
  expected_slot_type?: string | null;
};

export type CapabilityRepairHintDto = {
  role_name?: string;
  action_type?: string;
  action_key?: string;
  action_label?: string;
};

export type CapabilityOutputItemDto = {
  artifact_role?: string;
  logical_name?: string;
};

export type CatalogSummaryDto = {
  catalog_asset_count?: number;
  catalog_ready_asset_count?: number;
  catalog_blacklisted_asset_count?: number;
  catalog_role_coverage_count?: number;
  catalog_ready_role_names?: string[];
  catalog_source?: string;
};

export type CatalogConsistencyDto = {
  scope?: string;
  catalog_source?: string;
  current_catalog_source?: string;
  waiting_context_catalog_source?: string;
  waiting_context_catalog_present?: boolean;
  waiting_context_matches_current_catalog?: boolean;
  stale_missing_slots?: string[];
  expected_role_names?: string[];
  missing_catalog_roles?: string[];
  covered?: boolean;
  catalog_ready_role_names?: string[];
};

export type TaskDetailResponse = {
  task_id: string;
  state: string;
  state_version: number;
  planning_revision?: number;
  checkpoint_version?: number;
  resume_transaction?: ResumeTransaction;
  corruption_state?: CorruptionState;
  promotion_status?: string;
  graph_digest?: string;
  planning_summary?: Record<string, unknown>;
  planning_intent_status?: string;
  binding_status?: string;
  overruled_fields?: string[];
  blocked_mutations?: string[];
  assembly_blocked?: boolean;
  cognition_verdict?: string;
  catalog_summary?: CatalogSummaryDto;
  catalog_consistency?: CatalogConsistencyDto;
  case_projection?: CaseProjectionDto;
  goal_route_cognition?: CognitionMetadataDto;
  goal_route_output?: Record<string, unknown>;
  passb_cognition?: CognitionMetadataDto;
  passb_output?: Record<string, unknown>;
  repair_proposal_cognition?: CognitionMetadataDto;
  repair_proposal_output?: Record<string, unknown>;
  final_explanation_cognition?: CognitionMetadataDto;
  final_explanation_output?: Record<string, unknown>;
  latest_result_bundle_id?: string;
  latest_workspace_id?: string;
  pass1_summary?: {
    capability_key?: string;
    selected_template: string;
    logical_input_roles_count: number;
    required_roles_count?: number;
    optional_roles_count?: number;
    role_arg_mapping_count?: number;
    slot_schema_view_version: string;
    stable_defaults?: {
      analysis_template?: string;
      root_depth_factor?: number;
      pawc_factor?: number;
    };
  };
  goal_parse_summary?: {
    goal_type?: string;
    user_query?: string;
    analysis_kind?: string;
    intent_mode?: string;
    entities?: string[];
    source?: string;
  };
  skill_route_summary?: {
    route_mode?: string;
    primary_skill?: string;
    capability_key?: string;
    route_source?: string;
    confidence?: number;
    selected_template?: string;
    template_version?: string;
    execution_mode?: string;
    provider_preference?: string;
    runtime_profile_preference?: string;
    source?: string;
  };
  slot_bindings_summary?: {
    bound_slots_count: number;
    bound_role_names: string[];
  };
  args_draft_summary?: {
    param_count: number;
    param_keys: string[];
  };
  validation_summary?: {
    is_valid: boolean;
    missing_roles: string[];
    missing_params: string[];
    error_code: string;
    invalid_bindings?: string[];
  };
  input_chain_status?: "COMPLETE" | "INCOMPLETE" | string;
  job?: {
    job_id: string;
    job_state: string;
    last_heartbeat_at?: string;
    provider_key?: string;
    capability_key?: string;
    runtime_profile?: string;
    case_id?: string;
  };
  pass2_summary?: {
    planner?: string;
    node_count?: number;
    edge_count?: number;
    validation_is_valid?: boolean;
    validation_error_code?: string;
    capability_key?: string;
    template?: string;
    runtime_assertion_count?: number;
    graph_digest?: string;
    canonicalization_summary?: Record<string, unknown>;
    rewrite_summary?: Record<string, unknown>;
  };
  result_object_summary?: {
    result_id?: string;
    summary?: string;
    artifact_count?: number;
    created_at?: string;
    assertion_id?: string;
    node_id?: string;
    repairable?: boolean;
    details?: Record<string, unknown>;
  };
  result_bundle_summary?: {
    result_id?: string;
    summary?: string;
    main_output_count?: number;
    main_outputs?: string[];
    primary_outputs?: string[];
    audit_artifacts?: string[];
    created_at?: string;
  };
  final_explanation_summary?: {
    available?: boolean;
    title?: string;
    highlight_count?: number;
    generated_at?: string;
    failure_code?: string | null;
    failure_message?: string | null;
  };
  last_failure_summary?: {
    failure_code?: string;
    failure_message?: string;
    created_at?: string;
    assertion_id?: string;
    node_id?: string;
    repairable?: boolean;
    details?: Record<string, unknown>;
  };
  waiting_context?: {
    waiting_reason_type?: string;
    missing_slots?: { slot_name: string; expected_type: string; required: boolean }[];
    invalid_bindings?: string[];
    required_user_actions?: { action_type: string; key: string; label: string; required: boolean }[];
    resume_hint?: string;
    can_resume?: boolean;
    catalog_summary?: CatalogSummaryDto;
  };
  repair_proposal?: {
    available?: boolean;
    user_facing_reason?: string;
    resume_hint?: string;
    action_explanations?: { key: string; message: string }[];
    notes?: string[];
    failure_code?: string | null;
    failure_message?: string | null;
  };
};

export function getTask(taskId: string): Promise<TaskDetailResponse> {
  return apiFetch<TaskDetailResponse>(`/tasks/${taskId}`, {
    method: "GET",
    withAuth: true,
  });
}

export type TaskEventsResponse = {
  items: {
    event_type: string;
    from_state?: string;
    to_state?: string;
    state_version: number;
    created_at: string;
  }[];
};

export function getTaskEvents(taskId: string): Promise<TaskEventsResponse> {
  return apiFetch<TaskEventsResponse>(`/tasks/${taskId}/events`, {
    method: "GET",
    withAuth: true,
  });
}

export type TaskManifestResponse = {
  manifest_id: string;
  task_id: string;
  attempt_no: number;
  manifest_version: number;
  freeze_status?: string;
  planning_revision?: number;
  checkpoint_version?: number;
  graph_digest?: string;
  planning_summary?: Record<string, unknown>;
  canonicalization_summary?: Record<string, unknown>;
  rewrite_summary?: Record<string, unknown>;
  resume_transaction?: ResumeTransaction;
  corruption_state?: CorruptionState;
  promotion_status?: string;
  planning_intent_status?: string;
  binding_status?: string;
  overruled_fields?: string[];
  blocked_mutations?: string[];
  assembly_blocked?: boolean;
  cognition_verdict?: string;
  catalog_summary?: CatalogSummaryDto;
  catalog_consistency?: CatalogConsistencyDto;
  case_projection?: CaseProjectionDto;
  goal_route_cognition?: CognitionMetadataDto;
  goal_route_output?: Record<string, unknown>;
  passb_cognition?: CognitionMetadataDto;
  passb_output?: Record<string, unknown>;
  repair_proposal_cognition?: CognitionMetadataDto;
  repair_proposal_output?: Record<string, unknown>;
  final_explanation_cognition?: CognitionMetadataDto;
  final_explanation_output?: Record<string, unknown>;
  goal_parse?: {
    goal_type?: string;
    user_query?: string;
    analysis_kind?: string;
    intent_mode?: string;
    entities?: string[];
    source?: string;
  };
  skill_route?: {
    route_mode?: string;
    primary_skill?: string;
    capability_key?: string;
    route_source?: string;
    confidence?: number;
    selected_template?: string;
    template_version?: string;
    execution_mode?: string;
    provider_preference?: string;
    runtime_profile_preference?: string;
    source?: string;
  };
  capability_key?: string;
  capability_facts?: {
    capability_key?: string;
    display_name?: string;
    validation_hints?: CapabilityValidationHintDto[];
    repair_hints?: CapabilityRepairHintDto[];
    output_contract?: {
      outputs?: CapabilityOutputItemDto[];
    };
    runtime_profile_hint?: string;
  };
  logical_input_roles?: {
    role_name?: string;
    required?: boolean;
  }[];
  role_arg_mappings?: {
    role_name?: string;
    slot_arg_key?: string;
    value_arg_key?: string | null;
    default_value?: string | number | boolean | null;
  }[];
  stable_defaults?: {
    analysis_template?: string;
    root_depth_factor?: number;
    pawc_factor?: number;
  };
  slot_schema_view?: {
    slots?: {
      slot_name?: string;
      type?: string;
      bound_role?: string | null;
    }[];
  };
  slot_bindings?: {
    role_name?: string;
    slot_name?: string;
    source?: string;
  }[];
  args_draft?: Record<string, unknown>;
  validation_summary?: {
    is_valid?: boolean;
    missing_roles?: string[];
    missing_params?: string[];
    error_code?: string;
    invalid_bindings?: string[];
  };
  execution_graph?: {
    nodes?: {
      node_id?: string;
      kind?: string;
    }[];
    edges?: string[][];
  };
  runtime_assertions?: {
    assertion_id?: string;
    name?: string;
    required?: boolean;
    message?: string;
    assertion_type?: string;
    node_id?: string;
    target_key?: string;
    expected_value?: string;
    repairable?: boolean;
    details?: Record<string, unknown>;
  }[];
  created_at?: string;
};

export function getTaskManifest(taskId: string): Promise<TaskManifestResponse> {
  return apiFetch<TaskManifestResponse>(`/tasks/${taskId}/manifest`, {
    method: "GET",
    withAuth: true,
  });
}

export type TaskStreamResponse = {
  task: TaskDetailResponse;
  events: TaskEventsResponse;
};

export type TaskResultResponse = {
  task_id: string;
  job_id?: string;
  task_state: string;
  job_state?: string;
  provider_key?: string;
  runtime_profile?: string;
  case_id?: string;
  resume_transaction?: ResumeTransaction;
  corruption_state?: CorruptionState;
  freeze_status?: string;
  planning_revision?: number;
  checkpoint_version?: number;
  graph_digest?: string;
  planning_summary?: Record<string, unknown>;
  canonicalization_summary?: Record<string, unknown>;
  rewrite_summary?: Record<string, unknown>;
  promotion_status?: string;
  planning_intent_status?: string;
  binding_status?: string;
  overruled_fields?: string[];
  blocked_mutations?: string[];
  assembly_blocked?: boolean;
  cognition_verdict?: string;
  catalog_summary?: CatalogSummaryDto;
  catalog_consistency?: CatalogConsistencyDto;
  case_projection?: CaseProjectionDto;
  goal_route_cognition?: CognitionMetadataDto;
  goal_route_output?: Record<string, unknown>;
  passb_cognition?: CognitionMetadataDto;
  passb_output?: Record<string, unknown>;
  repair_proposal_cognition?: CognitionMetadataDto;
  repair_proposal_output?: Record<string, unknown>;
  final_explanation_cognition?: CognitionMetadataDto;
  final_explanation_output?: Record<string, unknown>;
  result_bundle?: {
    result_id?: string;
    task_id?: string;
    job_id?: string;
    summary?: string;
    metrics?: Record<string, unknown>;
    output_registry?: Record<string, unknown>;
    primary_output_refs?: {
      output_id?: string;
      path?: string;
    }[];
    main_outputs?: string[];
    artifacts?: string[];
    primary_outputs?: string[];
    intermediate_outputs?: string[];
    audit_artifacts?: string[];
    derived_outputs?: string[];
    created_at?: string;
  };
  final_explanation?: {
    available?: boolean;
    title?: string;
    highlights?: string[];
    narrative?: string;
    generated_at?: string;
    failure_code?: string | null;
    failure_message?: string | null;
  };
  failure_summary?: {
    failure_code?: string;
    failure_message?: string;
    created_at?: string;
    assertion_id?: string;
    node_id?: string;
    repairable?: boolean;
    details?: Record<string, unknown>;
  };
  docker_runtime_evidence?: {
    container_name?: string;
    image?: string;
    workspace_output_path?: string;
    result_file_exists?: boolean;
    provider_key?: string;
    runtime_profile?: string;
    case_id?: string;
    case_descriptor_version?: string;
    contract_mode?: string;
    runtime_mode?: string;
    input_bindings?: {
      role_name?: string;
      slot_name?: string;
      source?: string;
      arg_key?: string;
      provider_input_path?: string;
      source_ref?: string;
    }[];
    promotion_status?: string;
  };
  workspace_summary?: WorkspaceSummary;
  artifact_catalog?: ArtifactCatalog;
};

export type ArtifactMeta = {
  artifact_id?: string;
  artifact_role?: string;
  logical_name?: string;
  relative_path?: string;
  absolute_path?: string;
  content_type?: string;
  size_bytes?: number;
  sha256?: string;
  created_at?: string;
};

export type ArtifactCatalog = {
  primary_outputs?: ArtifactMeta[];
  intermediate_outputs?: ArtifactMeta[];
  audit_artifacts?: ArtifactMeta[];
  derived_outputs?: ArtifactMeta[];
  logs?: ArtifactMeta[];
};

export type WorkspaceSummary = {
  workspace_id?: string;
  workspace_output_path?: string;
  archive_path?: string;
  cleanup_completed?: boolean;
  archive_completed?: boolean;
};

export function getTaskResult(taskId: string): Promise<TaskResultResponse> {
  return apiFetch<TaskResultResponse>(`/tasks/${taskId}/result`, {
    method: "GET",
    withAuth: true,
  });
}

export type TaskRunsResponse = {
  task_id: string;
  items: {
    attempt_no: number;
    job_id?: string;
    workspace_id?: string;
    job_state?: string;
    workspace_state?: string;
    result_bundle_id?: string;
    created_at?: string;
    finished_at?: string;
  }[];
};

export function getTaskRuns(taskId: string): Promise<TaskRunsResponse> {
  return apiFetch<TaskRunsResponse>(`/tasks/${taskId}/runs`, {
    method: "GET",
    withAuth: true,
  });
}

export type TaskArtifactsResponse = {
  task_id: string;
  items: {
    attempt_no: number;
    workspace?: {
      workspace_id?: string;
      workspace_state?: string;
      runtime_profile?: string;
      container_name?: string;
      host_workspace_path?: string;
      archive_path?: string;
      created_at?: string;
      started_at?: string;
      finished_at?: string;
      cleaned_at?: string;
      archived_at?: string;
    };
    artifacts?: ArtifactCatalog;
  }[];
};

export function getTaskArtifacts(taskId: string): Promise<TaskArtifactsResponse> {
  return apiFetch<TaskArtifactsResponse>(`/tasks/${taskId}/artifacts`, {
    method: "GET",
    withAuth: true,
  });
}

export type CancelTaskResponse = {
  task_id: string;
  job_id?: string;
  state: string;
  job_state?: string;
  accepted: boolean;
};

export function cancelTask(taskId: string): Promise<CancelTaskResponse> {
  return apiFetch<CancelTaskResponse>(`/tasks/${taskId}/cancel`, {
    method: "POST",
    withAuth: true,
    body: JSON.stringify({}),
  });
}

export type UploadAttachmentResponse = {
  attachment_id: string;
  task_id: string;
  logical_slot?: string;
  stored_path: string;
  size_bytes: number;
  created_at: string;
  assignment_status: "ASSIGNED" | "UNASSIGNED" | string;
};

export async function uploadAttachment(taskId: string, file: File, logicalSlot?: string): Promise<UploadAttachmentResponse> {
  const token = getAccessToken();
  if (!token) {
    throw new Error("未登录，请先登录");
  }
  const formData = new FormData();
  formData.append("file", file);
  if (logicalSlot && logicalSlot.trim()) {
    formData.append("logical_slot", logicalSlot.trim());
  }

  const response = await fetch(`${API_BASE_URL}/tasks/${taskId}/attachments`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
    body: formData,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `上传失败: ${response.status}`);
  }
  return (await response.json()) as UploadAttachmentResponse;
}

export type ResumeTaskResponse = {
  task_id: string;
  state: string;
  state_version: number;
  resume_accepted: boolean;
  resume_attempt: number;
};

export type ForceRevertCheckpointResponse = {
  task_id: string;
  state: string;
  state_version: number;
  checkpoint_version?: number;
  manifest_id?: string;
};

export function resumeTask(
  taskId: string,
  payload: {
    resume_request_id: string;
    attachment_ids?: string[];
    slot_overrides?: Record<string, unknown>;
    args_overrides?: Record<string, unknown>;
    user_note?: string;
  },
): Promise<ResumeTaskResponse> {
  return apiFetch<ResumeTaskResponse>(`/tasks/${taskId}/resume`, {
    method: "POST",
    withAuth: true,
    body: JSON.stringify(payload),
  });
}

export function forceRevertCheckpoint(
  taskId: string,
  payload: { request_id: string; target_checkpoint_version: number },
): Promise<ForceRevertCheckpointResponse> {
  return apiFetch<ForceRevertCheckpointResponse>(`/tasks/${taskId}/force-revert-checkpoint`, {
    method: "POST",
    withAuth: true,
    body: JSON.stringify(payload),
  });
}

export function subscribeTaskStream(
  taskId: string,
  handlers: {
    onUpdate: (payload: TaskStreamResponse) => void;
    onError: (error: Error) => void;
  },
): () => void {
  const token = getAccessToken();
  if (!token) {
    throw new Error("未登录，请先登录");
  }

  const abortController = new AbortController();
  void fetchEventSource(`${API_BASE_URL}/tasks/${taskId}/stream`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "text/event-stream",
    },
    signal: abortController.signal,
    openWhenHidden: true,
    onmessage(event) {
      if (event.event !== "task_update") {
        return;
      }
      try {
        handlers.onUpdate(JSON.parse(event.data) as TaskStreamResponse);
      } catch (error) {
        handlers.onError(error instanceof Error ? error : new Error("SSE data parse failed"));
      }
    },
    onerror(error) {
      handlers.onError(error instanceof Error ? error : new Error("SSE connection failed"));
      throw error;
    },
  }).catch((error) => {
    if (!abortController.signal.aborted) {
      handlers.onError(error instanceof Error ? error : new Error("SSE subscribe failed"));
    }
  });

  return () => {
    abortController.abort();
  };
}

export type SessionMessage = {
  message_id: string;
  session_id: string;
  task_id?: string | null;
  result_bundle_id?: string | null;
  role: string;
  message_type: string;
  content?: Record<string, unknown> | null;
  attachment_refs?: Record<string, unknown> | null;
  action_schema?: Record<string, unknown> | null;
  related_object_refs?: Record<string, unknown> | null;
  created_at?: string | null;
};

export type AnalysisSessionResponse = {
  session_id: string;
  title?: string | null;
  user_goal: string;
  status: string;
  scene_id?: string | null;
  current_task_id?: string | null;
  latest_result_bundle_id?: string | null;
  created_at?: string | null;
  updated_at?: string | null;
  current_required_user_action?: Record<string, unknown> | null;
  session_context_summary?: Record<string, unknown> | null;
  current_task_summary?: Record<string, unknown> | null;
  latest_result_summary?: Record<string, unknown> | null;
  progress_projection?: Record<string, unknown> | null;
  waiting_projection?: Record<string, unknown> | null;
};

export type CreateSessionResponse = AnalysisSessionResponse;

export type SessionMessagesResponse = {
  session_id: string;
  items: SessionMessage[];
};

export type SessionListItem = {
  session_id: string;
  title?: string | null;
  user_goal: string;
  status: string;
  scene_id?: string | null;
  current_task_id?: string | null;
  latest_result_bundle_id?: string | null;
  session_summary?: Record<string, unknown> | null;
  created_at?: string | null;
  updated_at?: string | null;
};

export type SessionListResponse = {
  items: SessionListItem[];
  summary?: {
    total_sessions?: number;
    needs_action_count?: number;
    running_count?: number;
    ready_results_count?: number;
    priority_sessions?: SessionListItem[];
  };
};

export type UploadSessionAttachmentResponse = {
  session_id: string;
  task_id: string;
  attachment_id: string;
  logical_slot?: string | null;
  stored_path?: string | null;
  size_bytes?: number | null;
  created_at?: string | null;
  assignment_status?: string | null;
};

export type SessionStreamResponse = {
  session: AnalysisSessionResponse;
  messages: SessionMessagesResponse;
  progress_projection?: Record<string, unknown> | null;
  waiting_projection?: Record<string, unknown> | null;
  latest_result_summary?: Record<string, unknown> | null;
};

export function createSession(payload: {
  user_goal: string;
  title?: string;
  scene_id?: string;
}): Promise<CreateSessionResponse> {
  return apiFetch<CreateSessionResponse>("/sessions", {
    method: "POST",
    withAuth: true,
    body: JSON.stringify(payload),
  });
}

export function listSessions(params?: {
  status?: string;
  scene_id?: string;
  limit?: number;
}): Promise<SessionListResponse> {
  const search = new URLSearchParams();
  if (params?.status) search.set("status", params.status);
  if (params?.scene_id) search.set("scene_id", params.scene_id);
  if (params?.limit) search.set("limit", String(params.limit));
  const queryString = search.toString();
  const query = queryString ? `?${queryString}` : "";
  return apiFetch<SessionListResponse>(`/sessions${query}`, {
    method: "GET",
    withAuth: true,
  });
}

export function getSession(sessionId: string): Promise<AnalysisSessionResponse> {
  return apiFetch<AnalysisSessionResponse>(`/sessions/${sessionId}`, {
    method: "GET",
    withAuth: true,
  });
}

export function getSessionMessages(sessionId: string): Promise<SessionMessagesResponse> {
  return apiFetch<SessionMessagesResponse>(`/sessions/${sessionId}/messages`, {
    method: "GET",
    withAuth: true,
  });
}

export function postSessionMessage(
  sessionId: string,
  payload: {
    content: string;
    client_request_id: string;
    attachment_ids?: string[];
    slot_overrides?: Record<string, unknown>;
    args_overrides?: Record<string, unknown>;
  },
): Promise<AnalysisSessionResponse> {
  return apiFetch<AnalysisSessionResponse>(`/sessions/${sessionId}/messages`, {
    method: "POST",
    withAuth: true,
    body: JSON.stringify(payload),
  });
}

export function uploadSessionAttachment(
  sessionId: string,
  file: File,
  logicalSlot?: string,
): Promise<UploadSessionAttachmentResponse> {
  const formData = new FormData();
  formData.append("file", file);
  if (logicalSlot) {
    formData.append("logical_slot", logicalSlot);
  }
  return apiFetch<UploadSessionAttachmentResponse>(`/sessions/${sessionId}/attachments`, {
    method: "POST",
    withAuth: true,
    body: formData,
  });
}

export function subscribeSessionStream(
  sessionId: string,
  handlers: {
    onUpdate: (payload: SessionStreamResponse) => void;
    onError: (error: Error) => void;
  },
): () => void {
  const token = getAccessToken();
  if (!token) {
    throw new Error("鏈櫥褰曪紝璇峰厛鐧诲綍");
  }

  const abortController = new AbortController();
  void fetchEventSource(`${API_BASE_URL}/sessions/${sessionId}/stream`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "text/event-stream",
    },
    signal: abortController.signal,
    openWhenHidden: true,
    onmessage(event) {
      if (event.event !== "session_update") {
        return;
      }
      try {
        handlers.onUpdate(JSON.parse(event.data) as SessionStreamResponse);
      } catch (error) {
        handlers.onError(error instanceof Error ? error : new Error("Session SSE data parse failed"));
      }
    },
    onerror(error) {
      handlers.onError(error instanceof Error ? error : new Error("Session SSE connection failed"));
      throw error;
    },
  }).catch((error) => {
    if (!abortController.signal.aborted) {
      handlers.onError(error instanceof Error ? error : new Error("Session SSE subscribe failed"));
    }
  });

  return () => {
    abortController.abort();
  };
}
