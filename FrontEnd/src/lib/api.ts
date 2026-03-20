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

export type TaskDetailResponse = {
  task_id: string;
  state: string;
  state_version: number;
  latest_result_bundle_id?: string;
  latest_workspace_id?: string;
  pass1_summary?: {
    selected_template: string;
    logical_input_roles_count: number;
    slot_schema_view_version: string;
  };
  goal_parse_summary?: Record<string, unknown>;
  skill_route_summary?: Record<string, unknown>;
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
  };
  pass2_summary?: Record<string, unknown>;
  result_object_summary?: {
    result_id?: string;
    summary?: string;
    artifact_count?: number;
    created_at?: string;
  };
  result_bundle_summary?: {
    result_id?: string;
    summary?: string;
    main_output_count?: number;
    created_at?: string;
  };
  final_explanation_summary?: {
    title?: string;
    highlight_count?: number;
    generated_at?: string;
  };
  last_failure_summary?: {
    failure_code?: string;
    failure_message?: string;
    created_at?: string;
  };
  waiting_context?: {
    waiting_reason_type?: string;
    missing_slots?: { slot_name: string; expected_type: string; required: boolean }[];
    invalid_bindings?: string[];
    required_user_actions?: { action_type: string; key: string; label: string; required: boolean }[];
    resume_hint?: string;
    can_resume?: boolean;
  };
  repair_proposal?: {
    user_facing_reason?: string;
    resume_hint?: string;
    action_explanations?: { key: string; message: string }[];
    notes?: string[];
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
  goal_parse?: Record<string, unknown>;
  skill_route?: Record<string, unknown>;
  logical_input_roles?: unknown;
  slot_schema_view?: Record<string, unknown>;
  slot_bindings?: unknown;
  args_draft?: Record<string, unknown>;
  validation_summary?: Record<string, unknown>;
  execution_graph?: Record<string, unknown>;
  runtime_assertions?: Record<string, unknown>;
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
  result_bundle?: {
    result_id?: string;
    task_id?: string;
    job_id?: string;
    summary?: string;
    metrics?: Record<string, unknown>;
    main_outputs?: string[];
    artifacts?: string[];
    primary_outputs?: ArtifactMeta[];
    intermediate_outputs?: ArtifactMeta[];
    audit_artifacts?: ArtifactMeta[];
    derived_outputs?: ArtifactMeta[];
    created_at?: string;
  };
  final_explanation?: {
    title?: string;
    highlights?: string[];
    narrative?: string;
    generated_at?: string;
  };
  failure_summary?: {
    failure_code?: string;
    failure_message?: string;
    created_at?: string;
  };
  docker_runtime_evidence?: {
    container_name?: string;
    image?: string;
    workspace_output_path?: string;
    result_file_exists?: boolean;
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
