"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";

import { ArtifactMeta, getTaskArtifacts, TaskArtifactsResponse } from "@/lib/api";
import { getAccessToken } from "@/lib/auth";
import DebugJsonPanel from "@/components/DebugJsonPanel";

type AttemptArtifactView = TaskArtifactsResponse["items"][number];

function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === "") {
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

function WorkspacePanel({ workspace }: { workspace?: AttemptArtifactView["workspace"] }) {
  if (!workspace) {
    return <p className="muted">No workspace record.</p>;
  }

  return (
    <div className="kv-grid">
      <div className="kv-item">
        <span className="kv-key">workspace_id</span>
        <span className="kv-value">{workspace.workspace_id ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">workspace_state</span>
        <span className="kv-value">{workspace.workspace_state ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">runtime_profile</span>
        <span className="kv-value">{workspace.runtime_profile ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">container_name</span>
        <span className="kv-value">{workspace.container_name ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">host_workspace_path</span>
        <span className="kv-value break-all">{workspace.host_workspace_path ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">archive_path</span>
        <span className="kv-value break-all">{workspace.archive_path ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">created_at</span>
        <span className="kv-value">{workspace.created_at ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">started_at</span>
        <span className="kv-value">{workspace.started_at ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">finished_at</span>
        <span className="kv-value">{workspace.finished_at ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">cleaned_at</span>
        <span className="kv-value">{workspace.cleaned_at ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">archived_at</span>
        <span className="kv-value">{workspace.archived_at ?? "-"}</span>
      </div>
    </div>
  );
}

function ArtifactList({ items }: { items?: ArtifactMeta[] }) {
  if (!items || items.length === 0) {
    return <p className="muted">No artifacts.</p>;
  }

  return (
    <ul className="simple-list">
      {items.map((item, index) => (
        <li key={`${item.artifact_id ?? item.relative_path ?? "artifact"}-${index}`}>
          <strong>{item.logical_name ?? item.relative_path ?? item.artifact_id ?? "artifact"}</strong>
          {" | "}
          {item.artifact_role ?? "-"}
          {" | "}
          {item.relative_path ?? "-"}
          {" | "}
          {formatValue(item.size_bytes)} bytes
          {" | "}
          {item.content_type ?? "-"}
          {" | "}
          {item.sha256 ?? "-"}
        </li>
      ))}
    </ul>
  );
}

function ArtifactBucketPanel({
  title,
  items,
}: {
  title: string;
  items?: ArtifactMeta[];
}) {
  return (
    <>
      <h3>{title}</h3>
      <ArtifactList items={items} />
    </>
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
          <h3>Workspace</h3>
          <WorkspacePanel workspace={attempt.workspace} />
          <ArtifactBucketPanel title="Primary Outputs" items={attempt.artifacts?.primary_outputs} />
          <ArtifactBucketPanel title="Intermediate Outputs" items={attempt.artifacts?.intermediate_outputs} />
          <ArtifactBucketPanel title="Audit Artifacts" items={attempt.artifacts?.audit_artifacts} />
          <ArtifactBucketPanel title="Derived Outputs" items={attempt.artifacts?.derived_outputs} />
          <ArtifactBucketPanel title="Logs" items={attempt.artifacts?.logs} />
        </div>
      ))}

      {!loading && !error && (payload?.items?.length ?? 0) === 0 ? (
        <div className="card">
          <p className="muted">No artifact records yet.</p>
        </div>
      ) : null}

      <DebugJsonPanel title="Debug JSON" payload={payload} />
    </main>
  );
}
