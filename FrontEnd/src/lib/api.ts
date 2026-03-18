import { getAccessToken } from "@/lib/auth";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

type ApiOptions = RequestInit & {
  withAuth?: boolean;
};

async function apiFetch<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const headers = new Headers(options.headers ?? {});
  headers.set("Content-Type", "application/json");

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
  pass1_summary?: {
    selected_template: string;
    logical_input_roles_count: number;
    slot_schema_view_version: string;
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

