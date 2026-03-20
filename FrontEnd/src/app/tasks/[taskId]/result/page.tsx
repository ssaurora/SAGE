"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";

import { ArtifactMeta, getTaskResult, TaskResultResponse } from "@/lib/api";
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
  if (typeof value === "string") {
    return value;
  }
  return JSON.stringify(value);
}

function ResultMetricList({ metrics }: { metrics?: Record<string, unknown> }) {
  if (!metrics || Object.keys(metrics).length === 0) {
    return <p className="muted">No metrics available.</p>;
  }

  const entries = Object.entries(metrics).sort(([left], [right]) => left.localeCompare(right));
  return (
    <div className="kv-grid">
      {entries.map(([key, value]) => (
        <div className="kv-item" key={key}>
          <span className="kv-key">{key}</span>
          <span className="kv-value">{formatValue(value)}</span>
        </div>
      ))}
    </div>
  );
}

function ArtifactList({ artifacts }: { artifacts?: string[] }) {
  if (!artifacts || artifacts.length === 0) {
    return <p className="muted">No artifacts recorded.</p>;
  }
  return (
    <ul className="simple-list">
      {artifacts.map((artifact) => (
        <li key={artifact}>{artifact}</li>
      ))}
    </ul>
  );
}

function ArtifactMetaList({ items }: { items?: ArtifactMeta[] }) {
  if (!items || items.length === 0) {
    return <p className="muted">No artifacts recorded.</p>;
  }

  return (
    <ul className="simple-list">
      {items.map((artifact, index) => (
        <li key={`${artifact.artifact_id ?? artifact.relative_path ?? "artifact"}-${index}`}>
          {(artifact.logical_name ?? artifact.relative_path ?? artifact.artifact_id ?? "artifact")} | {artifact.relative_path ?? "-"} | {artifact.size_bytes ?? "-"} bytes
        </li>
      ))}
    </ul>
  );
}

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

  const resultBundle = result?.result_bundle;
  const finalExplanation = result?.final_explanation;
  const failureSummary = result?.failure_summary;
  const runtimeEvidence = result?.docker_runtime_evidence;
  const workspaceSummary = result?.workspace_summary;
  const artifactCatalog = result?.artifact_catalog;
  const metrics = resultBundle?.metrics;
  const isSuccess = result?.task_state === "SUCCEEDED";
  const isFailure = result?.task_state === "FAILED" || result?.task_state === "CANCELLED";

  return (
    <main className="container">
      <div className="card">
        <div className="row" style={{ justifyContent: "space-between", alignItems: "flex-start" }}>
          <div>
            <h1>Task Result</h1>
            <p className="muted">task_id: {taskId}</p>
            <p className="muted">job_id: {result?.job_id ?? "-"}</p>
          </div>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <span className="status">task {result?.task_state ?? "-"}</span>
            <span className="status">job {result?.job_state ?? "-"}</span>
          </div>
        </div>

        <div className="row" style={{ marginTop: 12 }}>
          <Link href={`/tasks/${taskId}`}>
            <button>Back to Detail</button>
          </Link>
          <Link href={`/tasks/${taskId}/artifacts`}>
            <button>View Artifacts</button>
          </Link>
        </div>

        {loading ? <p>Loading...</p> : null}
        {error ? <p className="error">{error}</p> : null}
        {!loading && !error && !resultBundle && !failureSummary ? (
          <p className="muted">Result is not ready yet. This page refreshes automatically.</p>
        ) : null}
      </div>

      <div className="card">
        <h2>Execution Summary</h2>
        <div className="kv-grid">
          <div className="kv-item">
            <span className="kv-key">result_id</span>
            <span className="kv-value">{resultBundle?.result_id ?? "-"}</span>
          </div>
          <div className="kv-item">
            <span className="kv-key">summary</span>
            <span className="kv-value">{resultBundle?.summary ?? failureSummary?.failure_message ?? "-"}</span>
          </div>
          <div className="kv-item">
            <span className="kv-key">created_at</span>
            <span className="kv-value">{resultBundle?.created_at ?? failureSummary?.created_at ?? "-"}</span>
          </div>
          <div className="kv-item">
            <span className="kv-key">main_outputs</span>
            <span className="kv-value">{resultBundle?.main_outputs?.join(", ") ?? "-"}</span>
          </div>
        </div>
      </div>

      <div className="card">
        <h2>Metrics</h2>
        {isSuccess ? <ResultMetricList metrics={metrics} /> : <p className="muted">Metrics are only available for successful runs.</p>}
      </div>

      <div className="card">
        <h2>Explanation</h2>
        {finalExplanation ? (
          <>
            <p><strong>{finalExplanation.title ?? "Result Explanation"}</strong></p>
            <p>{finalExplanation.narrative ?? "-"}</p>
            <h3>Highlights</h3>
            <ul className="simple-list">
              {(finalExplanation.highlights ?? []).map((item, index) => (
                <li key={`${item}-${index}`}>{item}</li>
              ))}
            </ul>
            <p className="muted">generated_at: {finalExplanation.generated_at ?? "-"}</p>
          </>
        ) : (
          <p className="muted">No final explanation available.</p>
        )}
      </div>

      <div className="card">
        <h2>Artifacts</h2>
        <ArtifactList artifacts={resultBundle?.artifacts} />
      </div>

      <div className="card">
        <h2>Grouped Outputs</h2>
        <h3>Primary Outputs</h3>
        <ArtifactMetaList items={artifactCatalog?.primary_outputs ?? resultBundle?.primary_outputs} />
        <h3>Intermediate Outputs</h3>
        <ArtifactMetaList items={artifactCatalog?.intermediate_outputs ?? resultBundle?.intermediate_outputs} />
        <h3>Audit Artifacts</h3>
        <ArtifactMetaList items={artifactCatalog?.audit_artifacts ?? resultBundle?.audit_artifacts} />
        <h3>Derived Outputs</h3>
        <ArtifactMetaList items={artifactCatalog?.derived_outputs ?? resultBundle?.derived_outputs} />
        <h3>Logs</h3>
        <ArtifactMetaList items={artifactCatalog?.logs} />
      </div>

      <div className="card">
        <h2>Workspace Summary</h2>
        {workspaceSummary ? (
          <div className="kv-grid">
            <div className="kv-item">
              <span className="kv-key">workspace_id</span>
              <span className="kv-value">{workspaceSummary.workspace_id ?? "-"}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">workspace_output_path</span>
              <span className="kv-value">{workspaceSummary.workspace_output_path ?? "-"}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">archive_path</span>
              <span className="kv-value">{workspaceSummary.archive_path ?? "-"}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">cleanup_completed</span>
              <span className="kv-value">{formatValue(workspaceSummary.cleanup_completed)}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">archive_completed</span>
              <span className="kv-value">{formatValue(workspaceSummary.archive_completed)}</span>
            </div>
          </div>
        ) : (
          <p className="muted">No workspace summary available.</p>
        )}
      </div>

      <div className="card">
        <h2>Runtime Evidence</h2>
        {runtimeEvidence ? (
          <div className="kv-grid">
            <div className="kv-item">
              <span className="kv-key">container_name</span>
              <span className="kv-value">{runtimeEvidence.container_name ?? "-"}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">image</span>
              <span className="kv-value">{runtimeEvidence.image ?? "-"}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">workspace_output_path</span>
              <span className="kv-value">{runtimeEvidence.workspace_output_path ?? "-"}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">result_file_exists</span>
              <span className="kv-value">{formatValue(runtimeEvidence.result_file_exists)}</span>
            </div>
          </div>
        ) : (
          <p className="muted">No runtime evidence available.</p>
        )}
      </div>

      {isFailure ? (
        <div className="card">
          <h2>Failure</h2>
          {failureSummary ? (
            <div className="kv-grid">
              <div className="kv-item">
                <span className="kv-key">failure_code</span>
                <span className="kv-value">{failureSummary.failure_code ?? "-"}</span>
              </div>
              <div className="kv-item">
                <span className="kv-key">failure_message</span>
                <span className="kv-value">{failureSummary.failure_message ?? "-"}</span>
              </div>
              <div className="kv-item">
                <span className="kv-key">created_at</span>
                <span className="kv-value">{failureSummary.created_at ?? "-"}</span>
              </div>
            </div>
          ) : (
            <p className="muted">No failure summary available.</p>
          )}
        </div>
      ) : null}

      <div className="card">
        <h2>Raw JSON</h2>
        <pre>{JSON.stringify(result ?? null, null, 2)}</pre>
      </div>
    </main>
  );
}
