"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";

import { ArtifactMeta, getTaskResult, TaskResultResponse } from "@/lib/api";
import { getAccessToken } from "@/lib/auth";
import DebugJsonPanel from "@/components/DebugJsonPanel";

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

function ArtifactNameList({ items }: { items?: string[] }) {
  if (!items || items.length === 0) {
    return <p className="muted">No artifacts recorded.</p>;
  }

  return (
    <ul className="simple-list">
      {items.map((artifact, index) => (
        <li key={`${artifact}-${index}`}>{artifact}</li>
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

function OutputReferenceList({
  items,
}: {
  items?: NonNullable<TaskResultResponse["result_bundle"]>["primary_output_refs"];
}) {
  if (!items || items.length === 0) {
    return <p className="muted">No primary output refs recorded.</p>;
  }

  return (
    <ul className="simple-list">
      {items.map((item, index) => (
        <li key={`${item.output_id ?? "output"}-${index}`}>
          {item.output_id ?? "-"} | {item.path ?? "-"}
        </li>
      ))}
    </ul>
  );
}

function InputBindingList({
  items,
}: {
  items?: NonNullable<TaskResultResponse["docker_runtime_evidence"]>["input_bindings"];
}) {
  if (!items || items.length === 0) {
    return <p className="muted">No provider input bindings recorded.</p>;
  }

  return (
    <ul className="simple-list">
      {items.map((binding, index) => (
        <li key={`${binding.role_name ?? "binding"}-${index}`}>
          {binding.role_name ?? "-"} | slot={binding.slot_name ?? "-"} | arg={binding.arg_key ?? "-"} | source={binding.source ?? "-"} | path={binding.provider_input_path ?? "-"}
        </li>
      ))}
    </ul>
  );
}

function ExecutionSummaryPanel({
  resultBundle,
  failureSummary,
}: {
  resultBundle?: TaskResultResponse["result_bundle"];
  failureSummary?: TaskResultResponse["failure_summary"];
}) {
  return (
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
  );
}

function FinalExplanationPanel({
  explanation,
}: {
  explanation?: TaskResultResponse["final_explanation"];
}) {
  if (!explanation) {
    return <p className="muted">No final explanation available.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-key">title</span>
          <span className="kv-value">{explanation.title ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">generated_at</span>
          <span className="kv-value">{explanation.generated_at ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">highlight_count</span>
          <span className="kv-value">{formatValue(explanation.highlights?.length)}</span>
        </div>
      </div>
      <h3>Narrative</h3>
      <p>{explanation.narrative ?? "-"}</p>
      <h3>Highlights</h3>
      <ul className="simple-list">
        {(explanation.highlights ?? []).map((item, index) => (
          <li key={`${item}-${index}`}>{item}</li>
        ))}
      </ul>
    </>
  );
}

function WorkspaceSummaryPanel({
  summary,
}: {
  summary?: TaskResultResponse["workspace_summary"];
}) {
  if (!summary) {
    return <p className="muted">No workspace summary available.</p>;
  }

  return (
    <div className="kv-grid">
      <div className="kv-item">
        <span className="kv-key">workspace_id</span>
        <span className="kv-value">{summary.workspace_id ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">workspace_output_path</span>
        <span className="kv-value">{summary.workspace_output_path ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">archive_path</span>
        <span className="kv-value">{summary.archive_path ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">cleanup_completed</span>
        <span className="kv-value">{formatValue(summary.cleanup_completed)}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">archive_completed</span>
        <span className="kv-value">{formatValue(summary.archive_completed)}</span>
      </div>
    </div>
  );
}

function RuntimeEvidencePanel({
  evidence,
}: {
  evidence?: TaskResultResponse["docker_runtime_evidence"];
}) {
  if (!evidence) {
    return <p className="muted">No runtime evidence available.</p>;
  }

  return (
    <div className="kv-grid">
      <div className="kv-item">
        <span className="kv-key">container_name</span>
        <span className="kv-value">{evidence.container_name ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">image</span>
        <span className="kv-value">{evidence.image ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">workspace_output_path</span>
        <span className="kv-value">{evidence.workspace_output_path ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">result_file_exists</span>
        <span className="kv-value">{formatValue(evidence.result_file_exists)}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">provider_key</span>
        <span className="kv-value">{evidence.provider_key ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">runtime_profile</span>
        <span className="kv-value">{evidence.runtime_profile ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">case_id</span>
        <span className="kv-value">{evidence.case_id ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">contract_mode</span>
        <span className="kv-value">{evidence.contract_mode ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">runtime_mode</span>
        <span className="kv-value">{evidence.runtime_mode ?? "-"}</span>
      </div>
    </div>
  );
}

function FailureSummaryPanel({
  summary,
}: {
  summary?: TaskResultResponse["failure_summary"];
}) {
  if (!summary) {
    return <p className="muted">No failure summary available.</p>;
  }

  return (
    <div className="kv-grid">
      <div className="kv-item">
        <span className="kv-key">failure_code</span>
        <span className="kv-value">{summary.failure_code ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">failure_message</span>
        <span className="kv-value">{summary.failure_message ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">created_at</span>
        <span className="kv-value">{summary.created_at ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">assertion_id</span>
        <span className="kv-value">{summary.assertion_id ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">node_id</span>
        <span className="kv-value">{summary.node_id ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">repairable</span>
        <span className="kv-value">{formatValue(summary.repairable)}</span>
      </div>
    </div>
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
            <p className="muted">provider_key: {result?.provider_key ?? runtimeEvidence?.provider_key ?? "-"}</p>
            <p className="muted">runtime_profile: {result?.runtime_profile ?? runtimeEvidence?.runtime_profile ?? "-"}</p>
            <p className="muted">case_id: {result?.case_id ?? runtimeEvidence?.case_id ?? "-"}</p>
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
        <ExecutionSummaryPanel resultBundle={resultBundle} failureSummary={failureSummary} />
      </div>

      <div className="card">
        <h2>Metrics</h2>
        {isSuccess ? <ResultMetricList metrics={metrics} /> : <p className="muted">Metrics are only available for successful runs.</p>}
      </div>

      <div className="card">
        <h2>Explanation</h2>
        <FinalExplanationPanel explanation={finalExplanation} />
      </div>

      <div className="card">
        <h2>Artifacts</h2>
        <ArtifactList artifacts={resultBundle?.artifacts} />
        <h3>Primary Output Refs</h3>
        <OutputReferenceList items={resultBundle?.primary_output_refs} />
      </div>

      <div className="card">
        <h2>Grouped Outputs</h2>
        <h3>Primary Outputs</h3>
        {artifactCatalog?.primary_outputs?.length ? (
          <ArtifactMetaList items={artifactCatalog.primary_outputs} />
        ) : (
          <ArtifactNameList items={resultBundle?.primary_outputs} />
        )}
        <h3>Intermediate Outputs</h3>
        {artifactCatalog?.intermediate_outputs?.length ? (
          <ArtifactMetaList items={artifactCatalog.intermediate_outputs} />
        ) : (
          <ArtifactNameList items={resultBundle?.intermediate_outputs} />
        )}
        <h3>Audit Artifacts</h3>
        {artifactCatalog?.audit_artifacts?.length ? (
          <ArtifactMetaList items={artifactCatalog.audit_artifacts} />
        ) : (
          <ArtifactNameList items={resultBundle?.audit_artifacts} />
        )}
        <h3>Derived Outputs</h3>
        {artifactCatalog?.derived_outputs?.length ? (
          <ArtifactMetaList items={artifactCatalog.derived_outputs} />
        ) : (
          <ArtifactNameList items={resultBundle?.derived_outputs} />
        )}
        <h3>Logs</h3>
        <ArtifactMetaList items={artifactCatalog?.logs} />
      </div>

      <div className="card">
        <h2>Workspace Summary</h2>
        <WorkspaceSummaryPanel summary={workspaceSummary} />
      </div>

      <div className="card">
        <h2>Runtime Evidence</h2>
        <RuntimeEvidencePanel evidence={runtimeEvidence} />
        <h3>Provider Input Bindings</h3>
        <InputBindingList items={runtimeEvidence?.input_bindings} />
      </div>

      <div className="card">
        <h2>Planning Compiler</h2>
        <DebugJsonPanel title="Planning Summary" payload={result?.planning_summary ?? null} defaultExpanded={false} />
        <DebugJsonPanel title="Canonicalization Summary" payload={result?.canonicalization_summary ?? null} defaultExpanded={false} />
        <DebugJsonPanel title="Rewrite Summary" payload={result?.rewrite_summary ?? null} defaultExpanded={false} />
        <DebugJsonPanel title="Output Registry" payload={resultBundle?.output_registry ?? null} defaultExpanded={false} />
      </div>

      {isFailure ? (
        <div className="card">
          <h2>Failure</h2>
          <FailureSummaryPanel summary={failureSummary} />
          <DebugJsonPanel title="Failure Details" payload={failureSummary?.details ?? null} defaultExpanded={false} />
        </div>
      ) : null}

      <DebugJsonPanel title="Debug JSON" payload={result ?? null} />
    </main>
  );
}
