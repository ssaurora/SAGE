"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";

import { getTask, getTaskEvents, TaskDetailResponse, TaskEventsResponse } from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

export default function TaskDetailPage() {
  const router = useRouter();
  const params = useParams<{ taskId: string }>();
  const taskId = useMemo(() => params.taskId, [params.taskId]);

  const [task, setTask] = useState<TaskDetailResponse | null>(null);
  const [events, setEvents] = useState<TaskEventsResponse["items"]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    const [taskResponse, eventsResponse] = await Promise.all([
      getTask(taskId),
      getTaskEvents(taskId),
    ]);
    setTask(taskResponse);
    setEvents(eventsResponse.items);
  }, [taskId]);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }

    let closed = false;

    async function initialize() {
      setLoading(true);
      setError(null);
      try {
        await loadData();
      } catch (e) {
        if (!closed) {
          setError(e instanceof Error ? e.message : "加载详情失败");
        }
      } finally {
        if (!closed) {
          setLoading(false);
        }
      }
    }

    initialize();

    const intervalId = window.setInterval(async () => {
      try {
        await loadData();
      } catch (e) {
        if (!closed) {
          setError(e instanceof Error ? e.message : "轮询失败");
        }
      }
    }, 3000);

    return () => {
      closed = true;
      window.clearInterval(intervalId);
    };
  }, [loadData, router]);

  return (
    <main className="container">
      <div className="card">
        <h1>任务详情</h1>
        <p className="muted">task_id: {taskId}</p>
        {loading ? <p>加载中...</p> : null}
        {error ? <p className="error">{error}</p> : null}

        {task ? (
          <div className="row" style={{ marginTop: 12 }}>
            <span className="status">{task.state}</span>
            <span className="muted">state_version: {task.state_version}</span>
          </div>
        ) : null}
      </div>

      <div className="card">
        <h2>Pass1 摘要</h2>
        {task?.pass1_summary ? (
          <ul>
            <li>selected_template: {task.pass1_summary.selected_template}</li>
            <li>logical_input_roles_count: {task.pass1_summary.logical_input_roles_count}</li>
            <li>slot_schema_view_version: {task.pass1_summary.slot_schema_view_version}</li>
          </ul>
        ) : (
          <p className="muted">暂无 Pass1 摘要。</p>
        )}
      </div>

      <div className="card">
        <h2>事件时间线</h2>
        {events.length === 0 ? (
          <p className="muted">暂无事件。</p>
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

      <div className="card">
        <h2>结果区占位</h2>
        <p className="muted">Week1 占位：后续周接入真实结果对象。</p>
      </div>

      <div className="card">
        <h2>错误区占位</h2>
        <p className="muted">Week1 占位：后续周接入结构化修复与错误详情。</p>
      </div>
    </main>
  );
}

