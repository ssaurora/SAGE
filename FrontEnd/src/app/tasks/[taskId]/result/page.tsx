"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";

import { ArtifactMeta, CognitionMetadataDto, getTaskResult, TaskResultResponse } from "@/lib/api";
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

function CognitionMetadataPanel({
  metadata,
  emptyText,
}: {
  metadata?: CognitionMetadataDto;
  emptyText: string;
}) {
  if (!metadata) {
    return <p className="muted">{emptyText}</p>;
  }

  return (
    <div className="kv-grid">
      <div className="kv-item"><span className="kv-key">provider</span><span className="kv-value">{metadata.provider ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">model</span><span className="kv-value">{metadata.model ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">source</span><span className="kv-value">{metadata.source ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">prompt_version</span><span className="kv-value">{metadata.prompt_version ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">fallback_used</span><span className="kv-value">{formatValue(metadata.fallback_used)}</span></div>
      <div className="kv-item"><span className="kv-key">schema_valid</span><span className="kv-value">{formatValue(metadata.schema_valid)}</span></div>
      <div className="kv-item"><span className="kv-key">status</span><span className="kv-value">{metadata.status ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">response_id</span><span className="kv-value">{metadata.response_id ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">failure_code</span><span className="kv-value">{metadata.failure_code ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">failure_message</span><span className="kv-value">{metadata.failure_message ?? "-"}</span></div>
    </div>
  );
}

function StageOutputPanel({
  title,
  payload,
  emptyText,
}: {
  title: string;
  payload?: Record<string, unknown> | null;
  emptyText: string;
}) {
  if (!payload || Object.keys(payload).length === 0) {
    return <p className="muted">{emptyText}</p>;
  }

  return <DebugJsonPanel title={title} payload={payload} defaultExpanded={false} />;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }
  return value as Record<string, unknown>;
}

function asRecordArray(value: unknown): Record<string, unknown>[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((item) => asRecord(item)).filter((item): item is Record<string, unknown> => item !== null);
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => (typeof item === "string" ? item : null))
    .filter((item): item is string => item !== null && item.trim().length > 0);
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

function GoalRouteReadablePanel({ payload }: { payload?: Record<string, unknown> | null }) {
  const goalParse = asRecord(payload?.goal_parse);
  const skillRoute = asRecord(payload?.skill_route);
  const decisionSummary = asRecord(payload?.decision_summary);
  const notes = asStringArray(decisionSummary?.notes);

  if (!payload) {
    return <p className="muted">No readable goal-route output available.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item"><span className="kv-key">planning_intent_status</span><span className="kv-value">{formatValue(payload.planning_intent_status)}</span></div>
        <div className="kv-item"><span className="kv-key">confidence</span><span className="kv-value">{formatValue(payload.confidence)}</span></div>
        <div className="kv-item"><span className="kv-key">decision_strategy</span><span className="kv-value">{formatValue(decisionSummary?.strategy)}</span></div>
        <div className="kv-item"><span className="kv-key">decision_status</span><span className="kv-value">{formatValue(decisionSummary?.status)}</span></div>
      </div>
      <h3>Goal Parse Facts</h3>
      <div className="kv-grid">
        <div className="kv-item"><span className="kv-key">goal_type</span><span className="kv-value">{formatValue(goalParse?.goal_type)}</span></div>
        <div className="kv-item"><span className="kv-key">analysis_kind</span><span className="kv-value">{formatValue(goalParse?.analysis_kind)}</span></div>
        <div className="kv-item"><span className="kv-key">intent_mode</span><span className="kv-value">{formatValue(goalParse?.intent_mode)}</span></div>
        <div className="kv-item"><span className="kv-key">source</span><span className="kv-value">{formatValue(goalParse?.source)}</span></div>
      </div>
      <h3>Skill Route Facts</h3>
      <div className="kv-grid">
        <div className="kv-item"><span className="kv-key">primary_skill</span><span className="kv-value">{formatValue(skillRoute?.primary_skill)}</span></div>
        <div className="kv-item"><span className="kv-key">capability_key</span><span className="kv-value">{formatValue(skillRoute?.capability_key)}</span></div>
        <div className="kv-item"><span className="kv-key">execution_mode</span><span className="kv-value">{formatValue(skillRoute?.execution_mode)}</span></div>
        <div className="kv-item"><span className="kv-key">provider_preference</span><span className="kv-value">{formatValue(skillRoute?.provider_preference)}</span></div>
        <div className="kv-item"><span className="kv-key">runtime_profile_preference</span><span className="kv-value">{formatValue(skillRoute?.runtime_profile_preference)}</span></div>
        <div className="kv-item"><span className="kv-key">selected_template</span><span className="kv-value">{formatValue(skillRoute?.selected_template)}</span></div>
      </div>
      <h3>Decision Notes</h3>
      <StringList values={notes} emptyText="No decision notes." />
    </>
  );
}

function PassBReadablePanel({ payload }: { payload?: Record<string, unknown> | null }) {
  const decisionSummary = asRecord(payload?.decision_summary);
  const slotBindings = asRecordArray(payload?.slot_bindings);
  const inferredArgs = asRecord(payload?.inferred_semantic_args);
  const userArgs = asRecord(payload?.user_semantic_args);
  const argsDraft = asRecord(payload?.args_draft);
  const assumptions = asStringArray(decisionSummary?.assumptions);

  if (!payload) {
    return <p className="muted">No readable passb output available.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item"><span className="kv-key">binding_status</span><span className="kv-value">{formatValue(payload.binding_status)}</span></div>
        <div className="kv-item"><span className="kv-key">confidence</span><span className="kv-value">{formatValue(payload.confidence)}</span></div>
        <div className="kv-item"><span className="kv-key">decision_strategy</span><span className="kv-value">{formatValue(decisionSummary?.strategy)}</span></div>
        <div className="kv-item"><span className="kv-key">slot_binding_count</span><span className="kv-value">{formatValue(slotBindings.length)}</span></div>
        <div className="kv-item"><span className="kv-key">args_draft_count</span><span className="kv-value">{formatValue(argsDraft ? Object.keys(argsDraft).length : 0)}</span></div>
      </div>
      <h3>Slot Bindings</h3>
      {slotBindings.length > 0 ? (
        <ul className="simple-list">
          {slotBindings.map((binding, index) => (
            <li key={`${formatValue(binding.role_name)}-${formatValue(binding.slot_name)}-${index}`}>
              {formatValue(binding.role_name)} {"->"} {formatValue(binding.slot_name)} | source={formatValue(binding.source)}
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted">No slot bindings.</p>
      )}
      <h3>User Semantic Args</h3>
      {userArgs && Object.keys(userArgs).length > 0 ? (
        <div className="kv-grid">
          {Object.entries(userArgs).map(([key, value]) => (
            <div className="kv-item" key={key}><span className="kv-key">{key}</span><span className="kv-value">{formatValue(value)}</span></div>
          ))}
        </div>
      ) : (
        <p className="muted">No user semantic args.</p>
      )}
      <h3>Inferred Semantic Args</h3>
      {inferredArgs && Object.keys(inferredArgs).length > 0 ? (
        <ul className="simple-list">
          {Object.entries(inferredArgs).map(([key, value]) => {
            const item = asRecord(value);
            return (
              <li key={key}>
                {key} = {formatValue(item?.value)} | reason={formatValue(item?.reason)} | source={formatValue(item?.source)}
              </li>
            );
          })}
        </ul>
      ) : (
        <p className="muted">No inferred semantic args.</p>
      )}
      <h3>Decision Assumptions</h3>
      <StringList values={assumptions} emptyText="No decision assumptions." />
    </>
  );
}

function RepairProposalReadablePanel({ payload }: { payload?: Record<string, unknown> | null }) {
  const actions = asRecordArray(payload?.action_explanations);
  const notes = asStringArray(payload?.notes);

  if (!payload) {
    return <p className="muted">No readable repair proposal output available.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item"><span className="kv-key">available</span><span className="kv-value">{formatValue(payload.available)}</span></div>
        <div className="kv-item"><span className="kv-key">failure_code</span><span className="kv-value">{formatValue(payload.failure_code)}</span></div>
      </div>
      <h3>User Facing Reason</h3>
      <p>{formatValue(payload.user_facing_reason)}</p>
      <h3>Resume Hint</h3>
      <p>{formatValue(payload.resume_hint)}</p>
      <h3>Action Explanations</h3>
      {actions.length > 0 ? (
        <ul className="simple-list">
          {actions.map((action, index) => (
            <li key={`${formatValue(action.key)}-${index}`}>
              {formatValue(action.key)} | {formatValue(action.message)}
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted">No action explanations.</p>
      )}
      <h3>Notes</h3>
      <StringList values={notes} emptyText="No notes." />
    </>
  );
}

function FinalExplanationReadablePanel({ payload }: { payload?: Record<string, unknown> | null }) {
  const highlights = asStringArray(payload?.highlights);

  if (!payload) {
    return <p className="muted">No readable final explanation output available.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item"><span className="kv-key">available</span><span className="kv-value">{formatValue(payload.available)}</span></div>
        <div className="kv-item"><span className="kv-key">title</span><span className="kv-value">{formatValue(payload.title)}</span></div>
        <div className="kv-item"><span className="kv-key">generated_at</span><span className="kv-value">{formatValue(payload.generated_at)}</span></div>
        <div className="kv-item"><span className="kv-key">highlight_count</span><span className="kv-value">{formatValue(highlights.length)}</span></div>
      </div>
      <h3>Narrative</h3>
      <p>{formatValue(payload.narrative)}</p>
      <h3>Highlights</h3>
      <StringList values={highlights} emptyText="No highlights." />
    </>
  );
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

  if (explanation.available === false) {
    return (
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-key">available</span>
          <span className="kv-value">{formatValue(explanation.available)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">failure_code</span>
          <span className="kv-value">{explanation.failure_code ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">failure_message</span>
          <span className="kv-value">{explanation.failure_message ?? "-"}</span>
        </div>
      </div>
    );
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-key">available</span>
          <span className="kv-value">{formatValue(explanation.available)}</span>
        </div>
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
        <h3>Final Explanation Cognition</h3>
        <CognitionMetadataPanel metadata={result?.final_explanation_cognition} emptyText="No final explanation cognition metadata." />
      </div>

      <div className="card">
        <h2>Artifacts</h2>
        <ArtifactList artifacts={resultBundle?.artifacts} />
        <h3>Primary Output Refs</h3>
        <OutputReferenceList items={resultBundle?.primary_output_refs} />
        <h3>Input Bindings</h3>
        <InputBindingList items={runtimeEvidence?.input_bindings} />
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
        <div className="kv-grid">
          <div className="kv-item">
            <span className="kv-key">planning_intent_status</span>
            <span className="kv-value">{result?.planning_intent_status ?? "-"}</span>
          </div>
          <div className="kv-item">
            <span className="kv-key">binding_status</span>
            <span className="kv-value">{result?.binding_status ?? "-"}</span>
          </div>
          <div className="kv-item">
            <span className="kv-key">cognition_verdict</span>
            <span className="kv-value">{result?.cognition_verdict ?? "-"}</span>
          </div>
          <div className="kv-item">
            <span className="kv-key">assembly_blocked</span>
            <span className="kv-value">{formatValue(result?.assembly_blocked)}</span>
          </div>
        </div>
        <h3>Overruled Fields</h3>
        <ArtifactNameList items={result?.overruled_fields} />
        <h3>Blocked Mutations</h3>
        <ArtifactNameList items={result?.blocked_mutations} />
        <h3>Goal Route Cognition</h3>
        <CognitionMetadataPanel metadata={result?.goal_route_cognition} emptyText="No goal-route cognition metadata." />
        <h3>PassB Cognition</h3>
        <CognitionMetadataPanel metadata={result?.passb_cognition} emptyText="No passb cognition metadata." />
        <h3>Repair Proposal Cognition</h3>
        <CognitionMetadataPanel metadata={result?.repair_proposal_cognition} emptyText="No repair proposal cognition metadata." />
        <DebugJsonPanel title="Planning Summary" payload={result?.planning_summary ?? null} defaultExpanded={false} />
        <DebugJsonPanel title="Canonicalization Summary" payload={result?.canonicalization_summary ?? null} defaultExpanded={false} />
        <DebugJsonPanel title="Rewrite Summary" payload={result?.rewrite_summary ?? null} defaultExpanded={false} />
        <DebugJsonPanel title="Output Registry" payload={resultBundle?.output_registry ?? null} defaultExpanded={false} />
      </div>

      <div className="card">
        <h2>LLM Trace</h2>
        <p className="muted">
          These are the structured outputs that the cognition layer produced across the task lifecycle.
        </p>
        <h3>Goal Route Output</h3>
        <GoalRouteReadablePanel payload={result?.goal_route_output} />
        <StageOutputPanel
          title="Goal Route Output"
          payload={result?.goal_route_output}
          emptyText="No goal-route output available."
        />
        <h3>PassB Output</h3>
        <PassBReadablePanel payload={result?.passb_output} />
        <StageOutputPanel
          title="PassB Output"
          payload={result?.passb_output}
          emptyText="No passb output available."
        />
        <h3>Repair Proposal Output</h3>
        <RepairProposalReadablePanel payload={result?.repair_proposal_output} />
        <StageOutputPanel
          title="Repair Proposal Output"
          payload={result?.repair_proposal_output}
          emptyText="No repair proposal output available."
        />
        <h3>Final Explanation Output</h3>
        <FinalExplanationReadablePanel payload={result?.final_explanation_output ?? (result?.final_explanation ? result.final_explanation as Record<string, unknown> : null)} />
        <StageOutputPanel
          title="Final Explanation Output"
          payload={result?.final_explanation_output ?? (result?.final_explanation ? result.final_explanation as Record<string, unknown> : null)}
          emptyText="No final explanation output available."
        />
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
