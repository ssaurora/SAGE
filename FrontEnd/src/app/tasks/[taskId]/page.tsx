"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";

import {
  cancelTask,
  getTask,
  getTaskEvents,
  resumeTask,
  subscribeTaskStream,
  TaskDetailResponse,
  TaskEventsResponse,
  uploadAttachment,
} from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

export default function TaskDetailPage() {
  const router = useRouter();
  const params = useParams<{ taskId: string }>();
  const taskId = useMemo(() => params.taskId, [params.taskId]);

  const [task, setTask] = useState<TaskDetailResponse | null>(null);
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
    const [taskResponse, eventsResponse] = await Promise.all([getTask(taskId), getTaskEvents(taskId)]);
    setTask(taskResponse);
    setEvents(eventsResponse.items);
  }, [taskId]);

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
      } catch (e) {
        if (!closed) {
          setError(e instanceof Error ? e.message : "Failed to load task detail");
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
        } catch (e) {
          if (!closed) {
            setError(e instanceof Error ? e.message : "Polling failed");
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
        onError(sseError) {
          if (!closed) {
            setError(`SSE failed, fallback to polling: ${sseError.message}`);
          }
          startPolling();
        },
      });
    } catch (e) {
      if (!closed) {
        setError(e instanceof Error ? e.message : "Failed to initialize SSE");
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

  async function handleCancel() {
    if (!canCancel || canceling) {
      return;
    }
    setCanceling(true);
    setError("Cancel request has been sent...");
    try {
      await cancelTask(taskId);
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Cancel failed");
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
    } catch (e) {
      setError(e instanceof Error ? e.message : "Upload failed");
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
    } catch (e) {
      setError(e instanceof Error ? e.message : "Resume failed");
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
        </div>
      </div>

      <div className="card">
        <h2>Job</h2>
        {task?.job ? (
          <ul>
            <li>job_id: {task.job.job_id}</li>
            <li>job_state: {task.job.job_state}</li>
            <li>last_heartbeat_at: {task.job.last_heartbeat_at ?? "-"}</li>
          </ul>
        ) : (
          <p className="muted">No job created.</p>
        )}
      </div>

      <div className="card">
        <h2>Pass1 Summary</h2>
        <pre>{JSON.stringify(task?.pass1_summary ?? null, null, 2)}</pre>
      </div>

      <div className="card">
        <h2>Slot Bindings Summary</h2>
        <pre>{JSON.stringify(task?.slot_bindings_summary ?? null, null, 2)}</pre>
      </div>

      <div className="card">
        <h2>Args Draft Summary</h2>
        <pre>{JSON.stringify(task?.args_draft_summary ?? null, null, 2)}</pre>
      </div>

      <div className="card">
        <h2>Validation Summary</h2>
        <pre>{JSON.stringify(task?.validation_summary ?? null, null, 2)}</pre>
      </div>

      <div className="card">
        <h2>Pass2 Summary</h2>
        <pre>{JSON.stringify(task?.pass2_summary ?? null, null, 2)}</pre>
      </div>

      <div className="card">
        <h2>Result Summaries</h2>
        <pre>{JSON.stringify({
          result_object_summary: task?.result_object_summary,
          result_bundle_summary: task?.result_bundle_summary,
          final_explanation_summary: task?.final_explanation_summary,
          last_failure_summary: task?.last_failure_summary,
        }, null, 2)}</pre>
      </div>

      {task?.state === "WAITING_USER" ? (
        <div className="card">
          <h2>Repair Panel (WAITING_USER)</h2>
          <pre>{JSON.stringify(task.waiting_context ?? null, null, 2)}</pre>
          <pre>{JSON.stringify(task.repair_proposal ?? null, null, 2)}</pre>
          <div className="row" style={{ marginTop: 12 }}>
            <input
              placeholder="logical_slot (optional)"
              value={logicalSlot}
              onChange={(event) => setLogicalSlot(event.target.value)}
            />
            <input
              type="file"
              onChange={(event) => void handleUpload(event.target.files?.[0] ?? null)}
            />
          </div>
          <div className="row" style={{ marginTop: 12 }}>
            <input
              placeholder="user_note (optional)"
              value={userNote}
              onChange={(event) => setUserNote(event.target.value)}
            />
            <button disabled={resuming || !task.waiting_context?.can_resume} onClick={handleResume}>
              {resuming ? "Resuming..." : "Resume"}
            </button>
          </div>
        </div>
      ) : null}

      <div className="card">
        <h2>Event Timeline</h2>
        {events.length === 0 ? (
          <p className="muted">No events yet.</p>
        ) : (
          <ul>
            {events.map((event, index) => (
              <li key={`${event.event_type}-${event.created_at}-${index}`}>
                <strong>{event.event_type}</strong> | version={event.state_version} | {event.created_at}
                {event.from_state || event.to_state
                  ? ` | ${event.from_state ?? "-"} -> ${event.to_state ?? "-"}`
                  : ""}
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  );
}
