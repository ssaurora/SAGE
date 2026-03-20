"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";

import { ArtifactMeta, getTaskArtifacts, TaskArtifactsResponse } from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

function ArtifactList({ items }: { items?: ArtifactMeta[] }) {
  if (!items || items.length === 0) {
    return <p className="muted">No artifacts.</p>;
  }

  return (
    <ul className="simple-list">
      {items.map((item, index) => (
        <li key={`${item.artifact_id ?? item.relative_path ?? "artifact"}-${index}`}>
          {item.logical_name ?? item.relative_path ?? item.artifact_id ?? "artifact"} | {item.artifact_role ?? "-"} | {item.relative_path ?? "-"} | {item.size_bytes ?? "-"} bytes | {item.sha256 ?? "-"}
        </li>
      ))}
    </ul>
  );
}

export default function TaskArtifactsPage() {
  const router = useRouter();
  const params = useParams<{ taskId: string }>();
  const taskId = useMemo(() => params.taskId, [params.taskId]);

  const [payload, setPayload] = useState<TaskArtifactsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
      return;
    }

    let closed = false;

    async function load() {
      try {
        const data = await getTaskArtifacts(taskId);
        if (!closed) {
          setPayload(data);
          setError(null);
        }
      } catch (loadError) {
        if (!closed) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load artifacts");
        }
      } finally {
        if (!closed) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      closed = true;
    };
  }, [router, taskId]);

  return (
    <main className="container">
      <div className="card">
        <h1>Task Artifacts</h1>
        <p className="muted">task_id: {taskId}</p>
        <div className="row" style={{ marginTop: 12 }}>
          <Link href={`/tasks/${taskId}`}>
            <button>Back to Detail</button>
          </Link>
          <Link href={`/tasks/${taskId}/result`}>
            <button>View Result</button>
          </Link>
        </div>
        {loading ? <p>Loading...</p> : null}
        {error ? <p className="error">{error}</p> : null}
      </div>

      {(payload?.items ?? []).map((attempt) => (
        <div className="card" key={`attempt-${attempt.attempt_no}`}>
          <h2>Attempt {attempt.attempt_no}</h2>
          <div className="kv-grid">
            <div className="kv-item">
              <span className="kv-key">workspace_id</span>
              <span className="kv-value">{attempt.workspace?.workspace_id ?? "-"}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">workspace_state</span>
              <span className="kv-value">{attempt.workspace?.workspace_state ?? "-"}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">runtime_profile</span>
              <span className="kv-value">{attempt.workspace?.runtime_profile ?? "-"}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">archive_path</span>
              <span className="kv-value">{attempt.workspace?.archive_path ?? "-"}</span>
            </div>
          </div>

          <h3>Primary Outputs</h3>
          <ArtifactList items={attempt.artifacts?.primary_outputs} />
          <h3>Intermediate Outputs</h3>
          <ArtifactList items={attempt.artifacts?.intermediate_outputs} />
          <h3>Audit Artifacts</h3>
          <ArtifactList items={attempt.artifacts?.audit_artifacts} />
          <h3>Derived Outputs</h3>
          <ArtifactList items={attempt.artifacts?.derived_outputs} />
          <h3>Logs</h3>
          <ArtifactList items={attempt.artifacts?.logs} />
        </div>
      ))}

      {!loading && !error && (payload?.items?.length ?? 0) === 0 ? (
        <div className="card">
          <p className="muted">No artifact records yet.</p>
        </div>
      ) : null}
    </main>
  );
}
