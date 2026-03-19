"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";

import { getTaskResult, TaskResultResponse } from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

export default function TaskResultPage() {
  const router = useRouter();
  const params = useParams<{ taskId: string }>();
  const taskId = useMemo(() => params.taskId, [params.taskId]);

  const [result, setResult] = useState<TaskResultResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }

    let closed = false;
    let pollingId: number | null = null;

    async function load() {
      try {
        const data = await getTaskResult(taskId);
        if (!closed) {
          setResult(data);
          setError(null);
        }
      } catch (e) {
        if (!closed) {
          setError(e instanceof Error ? e.message : "Failed to load result");
        }
      } finally {
        if (!closed) {
          setLoading(false);
        }
      }
    }

    void load();
    pollingId = window.setInterval(load, 3000);

    return () => {
      closed = true;
      if (pollingId !== null) {
        window.clearInterval(pollingId);
      }
    };
  }, [router, taskId]);

  return (
    <main className="container">
      <div className="card">
        <h1>Task Result</h1>
        <p className="muted">task_id: {taskId}</p>
        <p className="muted">task_state: {result?.task_state ?? "-"}</p>
        <p className="muted">job_state: {result?.job_state ?? "-"}</p>
        <div className="row" style={{ marginTop: 12 }}>
          <Link href={`/tasks/${taskId}`}>
            <button>Back to Detail</button>
          </Link>
        </div>
        {loading ? <p>Loading...</p> : null}
        {error ? <p className="error">{error}</p> : null}
      </div>

      <div className="card">
        <h2>Result Bundle</h2>
        <pre>{JSON.stringify(result?.result_bundle ?? null, null, 2)}</pre>
      </div>

      <div className="card">
        <h2>Final Explanation</h2>
        <pre>{JSON.stringify(result?.final_explanation ?? null, null, 2)}</pre>
      </div>

      <div className="card">
        <h2>Failure Summary</h2>
        <pre>{JSON.stringify(result?.failure_summary ?? null, null, 2)}</pre>
      </div>

      <div className="card">
        <h2>Docker Runtime Evidence</h2>
        <pre>{JSON.stringify(result?.docker_runtime_evidence ?? null, null, 2)}</pre>
      </div>
    </main>
  );
}

