"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";

import {
  cancelTask,
  getTask,
  getTaskEvents,
  getTaskManifest,
  getTaskRuns,
  resumeTask,
  subscribeTaskStream,
  TaskDetailResponse,
  TaskEventsResponse,
  TaskManifestResponse,
  TaskRunsResponse,
  uploadAttachment,
} from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

function formatValue(value: unknown): string {
  if (value === null || value === undefined) {
    return "-";
  }
  if (typeof value === "number") {
    return Number.isInteger(value) ? `${value}` : value.toFixed(6).replace(/0+$/, "").replace(/\.$/, "");
  }
  if (typeof value === "boolean") {
    return value ? "true" : "false";
  }
  if (Array.isArray(value)) {
    return value.length === 0 ? "-" : value.map((item) => formatValue(item)).join(", ");
  }
  if (typeof value === "string") {
    return value;
  }
  return JSON.stringify(value);
}

function KeyValueGrid({
  value,
  valueClassName,
}: {
  value?: Record<string, unknown> | null;
  valueClassName?: string;
}) {
  if (!value || Object.keys(value).length === 0) {
    return <p className="muted">No data available.</p>;
  }

  const valueClass = valueClassName ? `kv-value ${valueClassName}` : "kv-value";

  return (
    <div className="kv-grid">
      {Object.entries(value).map(([key, entryValue]) => (
        <div className="kv-item" key={key}>
          <span className="kv-key">{key}</span>
          <span className={valueClass}>{formatValue(entryValue)}</span>
        </div>
      ))}
    </div>
  );
}

function StringList({ values, emptyText }: { values?: string[]; emptyText: string }) {
  if (!values || values.length === 0) {
    return <p className="muted">{emptyText}</p>;
  }

  return (
    <ul className="simple-list">
      {values.map((value, index) => (
        <li key={`${value}-${index}`}>{value}</li>
      ))}
    </ul>
  );
}

export default function TaskDetailPage() {
  const router = useRouter();
  const params = useParams<{ taskId: string }>();
  const taskId = useMemo(() => params.taskId, [params.taskId]);

  const [task, setTask] = useState<TaskDetailResponse | null>(null);
  const [manifest, setManifest] = useState<TaskManifestResponse | null>(null);
  const [runs, setRuns] = useState<TaskRunsResponse["items"]>([]);
  const [events, setEvents] = useState<TaskEventsResponse["items"]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [streamMode, setStreamMode] = useState<"sse" | "polling">("sse");
  const [canceling, setCanceling] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [resuming, setResuming] = useState(false);
  const [logicalSlot, setLogicalSlot] = useState("");
  const [userNote, setUserNote] = useState("");

  const loadData = useCallback(async () => {
    const [taskResponse, eventsResponse, manifestResponse, runsResponse] = await Promise.all([
      getTask(taskId),
      getTaskEvents(taskId),
      getTaskManifest(taskId).catch(() => null),
      getTaskRuns(taskId).catch(() => ({ task_id: taskId, items: [] })),
    ]);

    setTask(taskResponse);
    setEvents(eventsResponse.items);
    setManifest(manifestResponse);
    setRuns(runsResponse.items);
    if (!logicalSlot && taskResponse.waiting_context?.missing_slots?.length) {
      setLogicalSlot(taskResponse.waiting_context.missing_slots[0].slot_name);
    }
  }, [logicalSlot, taskId]);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }

    let closed = false;
    let cleanupStream: (() => void) | null = null;
    let pollingId: number | null = null;

    async function initialize() {
      setLoading(true);
      setError(null);
      try {
        await loadData();
      } catch (loadError) {
        if (!closed) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load task detail");
        }
      } finally {
        if (!closed) {
          setLoading(false);
        }
      }
    }

    function startPolling() {
      if (pollingId !== null) {
        return;
      }
      setStreamMode("polling");
      pollingId = window.setInterval(async () => {
        try {
          await loadData();
        } catch (pollError) {
          if (!closed) {
            setError(pollError instanceof Error ? pollError.message : "Polling failed");
          }
        }
      }, 3000);
    }

    void initialize();

    try {
      cleanupStream = subscribeTaskStream(taskId, {
        onUpdate(payload) {
          if (closed) {
            return;
          }
          setStreamMode("sse");
          setTask(payload.task);
          setEvents(payload.events.items);
        },
        onError(streamError) {
          if (!closed) {
            setError(`SSE failed, fallback to polling: ${streamError.message}`);
          }
          startPolling();
        },
      });
    } catch (streamError) {
      if (!closed) {
        setError(streamError instanceof Error ? streamError.message : "Failed to initialize SSE");
      }
      startPolling();
    }

    return () => {
      closed = true;
      if (cleanupStream) {
        cleanupStream();
      }
      if (pollingId !== null) {
        window.clearInterval(pollingId);
      }
    };
  }, [loadData, router, taskId]);

  const canCancel = task?.state === "QUEUED" || task?.state === "RUNNING";
  const canResume = task?.waiting_context?.can_resume === true;
  const missingRoles = task?.validation_summary?.missing_roles;
  const invalidBindings = task?.validation_summary?.invalid_bindings;

  async function handleCancel() {
    if (!canCancel || canceling) {
      return;
    }
    setCanceling(true);
    setError("Cancel request has been sent...");
    try {
      await cancelTask(taskId);
      await loadData();
      setError(null);
    } catch (cancelError) {
      setError(cancelError instanceof Error ? cancelError.message : "Cancel failed");
    } finally {
      setCanceling(false);
    }
  }

  async function handleUpload(file: File | null) {
    if (!file || uploading) {
      return;
    }
    setUploading(true);
    setError("Uploading attachment...");
    try {
      await uploadAttachment(taskId, file, logicalSlot || undefined);
      await loadData();
      setError(null);
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "Upload failed");
    } finally {
      setUploading(false);
    }
  }

  async function handleResume() {
    if (resuming) {
      return;
    }
    setResuming(true);
    setError("Resume request has been sent...");
    try {
      await resumeTask(taskId, {
        resume_request_id: crypto.randomUUID(),
        user_note: userNote,
      });
      await loadData();
      setError(null);
    } catch (resumeError) {
      setError(resumeError instanceof Error ? resumeError.message : "Resume failed");
    } finally {
      setResuming(false);
    }
  }

  return (
    <main className="container">
      <div className="card">
        <h1>Task Detail</h1>
        <p className="muted">task_id: {taskId}</p>
        <p className="muted">update_mode: {streamMode}</p>
        <p className="muted">latest_result_bundle_id: {task?.latest_result_bundle_id ?? "-"}</p>
        <p className="muted">latest_workspace_id: {task?.latest_workspace_id ?? "-"}</p>
        {loading ? <p>Loading...</p> : null}
        {error ? <p className="error">{error}</p> : null}

        {task ? (
          <div className="row" style={{ marginTop: 12 }}>
            <span className="status">{task.state}</span>
            <span className="muted">state_version: {task.state_version}</span>
            <span className="muted">input_chain_status: {task.input_chain_status ?? "-"}</span>
          </div>
        ) : null}

        <div className="row" style={{ marginTop: 12 }}>
          <button disabled={!canCancel || canceling} onClick={handleCancel}>
            {canceling ? "Canceling..." : "Cancel Task"}
          </button>
          <Link href={`/tasks/${taskId}/result`}>
            <button>View Result Page</button>
          </Link>
          <Link href={`/tasks/${taskId}/artifacts`}>
            <button>View Artifacts</button>
          </Link>
        </div>
      </div>

      <div className="card">
        <h2>Goal Parse</h2>
        <KeyValueGrid value={task?.goal_parse_summary} />
      </div>

      <div className="card">
        <h2>Skill Route</h2>
        <KeyValueGrid value={task?.skill_route_summary} />
      </div>

      <div className="card">
        <h2>Manifest</h2>
        {manifest ? (
          <>
            <div className="kv-grid">
              <div className="kv-item">
                <span className="kv-key">manifest_id</span>
                <span className="kv-value">{manifest.manifest_id}</span>
              </div>
              <div className="kv-item">
                <span className="kv-key">manifest_version</span>
                <span className="kv-value">{manifest.manifest_version}</span>
              </div>
              <div className="kv-item">
                <span className="kv-key">attempt_no</span>
                <span className="kv-value">{manifest.attempt_no}</span>
              </div>
              <div className="kv-item">
                <span className="kv-key">created_at</span>
                <span className="kv-value">{manifest.created_at ?? "-"}</span>
              </div>
            </div>
            <h3>Runtime Assertions</h3>
            <KeyValueGrid value={manifest.runtime_assertions} />
          </>
        ) : (
          <p className="muted">Manifest is not frozen yet.</p>
        )}
      </div>

      <div className="card">
        <h2>Job</h2>
        {task?.job ? (
          <div className="kv-grid">
            <div className="kv-item">
              <span className="kv-key">job_id</span>
              <span className="kv-value">{task.job.job_id}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">job_state</span>
              <span className="kv-value">{task.job.job_state}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">last_heartbeat_at</span>
              <span className="kv-value">{task.job.last_heartbeat_at ?? "-"}</span>
            </div>
          </div>
        ) : (
          <p className="muted">No job created.</p>
        )}
      </div>

      <div className="card">
        <h2>Run History</h2>
        {runs.length === 0 ? (
          <p className="muted">No run history yet.</p>
        ) : (
          <ul className="simple-list">
            {runs.map((run) => (
              <li key={`${run.attempt_no}-${run.job_id ?? "no-job"}`}>
                attempt {run.attempt_no} | job {run.job_id ?? "-"} | workspace {run.workspace_id ?? "-"} | {run.job_state ?? "-"} / {run.workspace_state ?? "-"}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card">
        <h2>Pass1 Summary</h2>
        <KeyValueGrid value={task?.pass1_summary} />
      </div>

      <div className="card">
        <h2>Bindings</h2>
        <KeyValueGrid value={task?.slot_bindings_summary} />
      </div>

      <div className="card">
        <h2>Args Draft</h2>
        <KeyValueGrid value={task?.args_draft_summary} />
      </div>

      <div className="card">
        <h2>Validation</h2>
        <KeyValueGrid value={task?.validation_summary} />
        <h3>Missing Roles</h3>
        <StringList values={Array.isArray(missingRoles) ? missingRoles : undefined} emptyText="No missing roles." />
        <h3>Invalid Bindings</h3>
        <StringList values={Array.isArray(invalidBindings) ? invalidBindings : undefined} emptyText="No invalid bindings." />
      </div>

      <div className="card">
        <h2>Pass2 Summary</h2>
        <KeyValueGrid value={task?.pass2_summary} />
      </div>

      <div className="card">
        <h2>Result Summaries</h2>
        <KeyValueGrid
          value={{
            ...(task?.result_object_summary ?? {}),
            result_bundle_summary: task?.result_bundle_summary ?? null,
            final_explanation_summary: task?.final_explanation_summary ?? null,
            last_failure_summary: task?.last_failure_summary ?? null,
          }}
        />
      </div>

      {task?.state === "WAITING_USER" ? (
        <div className="card">
          <h2>Repair Panel</h2>
          <KeyValueGrid value={task.waiting_context} />
          <h3>Required User Actions</h3>
          <ul className="simple-list">
            {(task.waiting_context?.required_user_actions ?? []).map((action) => (
              <li key={action.key}>{`${action.label} [${action.key}]`}</li>
            ))}
          </ul>
          <h3>Repair Proposal</h3>
          <KeyValueGrid value={task.repair_proposal} valueClassName="llm-text" />
          <div className="row" style={{ marginTop: 12 }}>
            <input
              placeholder="logical_slot"
              value={logicalSlot}
              onChange={(event) => setLogicalSlot(event.target.value)}
            />
            <input type="file" onChange={(event) => void handleUpload(event.target.files?.[0] ?? null)} />
          </div>
          <div className="row" style={{ marginTop: 12 }}>
            <input
              placeholder="user note"
              value={userNote}
              onChange={(event) => setUserNote(event.target.value)}
            />
            <button disabled={!canResume || resuming} onClick={handleResume}>
              {resuming ? "Resuming..." : canResume ? "Resume" : "Resume Blocked"}
            </button>
          </div>
        </div>
      ) : null}

      <div className="card">
        <h2>Events</h2>
        {events.length === 0 ? (
          <p className="muted">No events yet.</p>
        ) : (
          <ul className="simple-list">
            {events.map((event, index) => (
              <li key={`${event.event_type}-${event.created_at}-${index}`}>
                {event.created_at} | {event.event_type} | {event.from_state ?? "-"} -&gt; {event.to_state ?? "-"}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card">
        <h2>Raw JSON</h2>
        <pre>{JSON.stringify({ task, manifest, events }, null, 2)}</pre>
      </div>
    </main>
  );
}
