"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";

import {
  CapabilityOutputItemDto,
  CapabilityRepairHintDto,
  CapabilityValidationHintDto,
  cancelTask,
  CognitionMetadataDto,
  forceRevertCheckpoint,
  getMe,
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
import DebugJsonPanel from "@/components/DebugJsonPanel";

type WaitingContextDto = NonNullable<TaskDetailResponse["waiting_context"]>;
type MissingSlotDto = NonNullable<WaitingContextDto["missing_slots"]>[number];
type RequiredUserActionDto = NonNullable<WaitingContextDto["required_user_actions"]>[number];
type RepairProposalDto = NonNullable<TaskDetailResponse["repair_proposal"]>;
type RepairActionExplanationDto = NonNullable<RepairProposalDto["action_explanations"]>[number];
type CaseProjectionView =
  | NonNullable<TaskDetailResponse["case_projection"]>
  | NonNullable<TaskManifestResponse["case_projection"]>;
type GoalParseView =
  | NonNullable<TaskDetailResponse["goal_parse_summary"]>
  | NonNullable<TaskManifestResponse["goal_parse"]>;
type SkillRouteView =
  | NonNullable<TaskDetailResponse["skill_route_summary"]>
  | NonNullable<TaskManifestResponse["skill_route"]>;

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
      <h4>Goal Parse</h4>
      <div className="kv-grid">
        <div className="kv-item"><span className="kv-key">goal_type</span><span className="kv-value">{formatValue(goalParse?.goal_type)}</span></div>
        <div className="kv-item"><span className="kv-key">analysis_kind</span><span className="kv-value">{formatValue(goalParse?.analysis_kind)}</span></div>
        <div className="kv-item"><span className="kv-key">intent_mode</span><span className="kv-value">{formatValue(goalParse?.intent_mode)}</span></div>
        <div className="kv-item"><span className="kv-key">source</span><span className="kv-value">{formatValue(goalParse?.source)}</span></div>
      </div>
      <h4>Skill Route</h4>
      <div className="kv-grid">
        <div className="kv-item"><span className="kv-key">primary_skill</span><span className="kv-value">{formatValue(skillRoute?.primary_skill)}</span></div>
        <div className="kv-item"><span className="kv-key">capability_key</span><span className="kv-value">{formatValue(skillRoute?.capability_key)}</span></div>
        <div className="kv-item"><span className="kv-key">execution_mode</span><span className="kv-value">{formatValue(skillRoute?.execution_mode)}</span></div>
        <div className="kv-item"><span className="kv-key">provider_preference</span><span className="kv-value">{formatValue(skillRoute?.provider_preference)}</span></div>
        <div className="kv-item"><span className="kv-key">runtime_profile_preference</span><span className="kv-value">{formatValue(skillRoute?.runtime_profile_preference)}</span></div>
        <div className="kv-item"><span className="kv-key">selected_template</span><span className="kv-value">{formatValue(skillRoute?.selected_template)}</span></div>
      </div>
      <h4>Decision Notes</h4>
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
      <h4>Slot Bindings</h4>
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
      <h4>User Semantic Args</h4>
      {userArgs && Object.keys(userArgs).length > 0 ? (
        <div className="kv-grid">
          {Object.entries(userArgs).map(([key, value]) => (
            <div className="kv-item" key={key}><span className="kv-key">{key}</span><span className="kv-value">{formatValue(value)}</span></div>
          ))}
        </div>
      ) : (
        <p className="muted">No user semantic args.</p>
      )}
      <h4>Inferred Semantic Args</h4>
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
      <h4>Decision Assumptions</h4>
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
      <h4>User Facing Reason</h4>
      <p>{formatValue(payload.user_facing_reason)}</p>
      <h4>Resume Hint</h4>
      <p>{formatValue(payload.resume_hint)}</p>
      <h4>Action Explanations</h4>
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
      <h4>Notes</h4>
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
      <h4>Narrative</h4>
      <p>{formatValue(payload.narrative)}</p>
      <h4>Highlights</h4>
      <StringList values={highlights} emptyText="No highlights." />
    </>
  );
}

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

function CaseProjectionPanel({
  projection,
}: {
  projection?: CaseProjectionView;
}) {
  if (!projection) {
    return <p className="muted">No governed case projection available.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item"><span className="kv-key">mode</span><span className="kv-value">{projection.mode ?? "-"}</span></div>
        <div className="kv-item"><span className="kv-key">selected_case_id</span><span className="kv-value">{projection.selected_case_id ?? "-"}</span></div>
        <div className="kv-item"><span className="kv-key">candidate_count</span><span className="kv-value">{formatValue(projection.candidate_case_ids?.length ?? 0)}</span></div>
        <div className="kv-item"><span className="kv-key">registry_version</span><span className="kv-value">{projection.registry_version ?? "-"}</span></div>
      </div>
      <h3>Clarify Prompt</h3>
      <p>{projection.clarify_prompt ?? "-"}</p>
      <h3>Candidate Cases</h3>
      <StringList values={projection.candidate_case_ids} emptyText="No candidate cases." />
      <h3>Decision Basis</h3>
      <StringList values={projection.decision_basis} emptyText="No decision basis recorded." />
    </>
  );
}

function hasClarifyCaseSelectionAction(waitingContext?: TaskDetailResponse["waiting_context"]): boolean {
  return Boolean(waitingContext?.required_user_actions?.some((action) => action.key === "clarify_case_selection"));
}

function GoalParsePanel({
  summary,
}: {
  summary?: GoalParseView;
}) {
  if (!summary) {
    return <p className="muted">No goal parse summary available.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-key">goal_type</span>
          <span className="kv-value">{summary.goal_type ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">analysis_kind</span>
          <span className="kv-value">{summary.analysis_kind ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">user_query</span>
          <span className="kv-value">{summary.user_query ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">intent_mode</span>
          <span className="kv-value">{summary.intent_mode ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">source</span>
          <span className="kv-value">{summary.source ?? "-"}</span>
        </div>
      </div>
      <h3>Entities</h3>
      <StringList values={summary.entities} emptyText="No entities." />
    </>
  );
}

function SkillRoutePanel({
  summary,
}: {
  summary?: SkillRouteView;
}) {
  if (!summary) {
    return <p className="muted">No skill route summary available.</p>;
  }

  return (
    <div className="kv-grid">
      <div className="kv-item">
        <span className="kv-key">route_mode</span>
        <span className="kv-value">{summary.route_mode ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">primary_skill</span>
        <span className="kv-value">{summary.primary_skill ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">capability_key</span>
        <span className="kv-value">{summary.capability_key ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">route_source</span>
        <span className="kv-value">{summary.route_source ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">confidence</span>
        <span className="kv-value">{formatValue(summary.confidence)}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">selected_template</span>
        <span className="kv-value">{summary.selected_template ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">template_version</span>
        <span className="kv-value">{summary.template_version ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">execution_mode</span>
        <span className="kv-value">{summary.execution_mode ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">provider_preference</span>
        <span className="kv-value">{summary.provider_preference ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">runtime_profile_preference</span>
        <span className="kv-value">{summary.runtime_profile_preference ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">source</span>
        <span className="kv-value">{summary.source ?? "-"}</span>
      </div>
    </div>
  );
}

function RoleArgMappingList({
  mappings,
}: {
  mappings?: TaskManifestResponse["role_arg_mappings"];
}) {
  if (!mappings || mappings.length === 0) {
    return <p className="muted">No role arg mappings.</p>;
  }

  return (
    <ul className="simple-list">
      {mappings.map((mapping, index) => (
        <li key={`${mapping.role_name ?? "role"}-${index}`}>
          {mapping.role_name ?? "-"} | slot arg {mapping.slot_arg_key ?? "-"} | value arg {mapping.value_arg_key ?? "-"} | default {formatValue(mapping.default_value)}
        </li>
      ))}
    </ul>
  );
}

function StableDefaultsPanel({
  defaults,
}: {
  defaults?:
    | NonNullable<TaskDetailResponse["pass1_summary"]>["stable_defaults"]
    | TaskManifestResponse["stable_defaults"];
}) {
  if (!defaults) {
    return <p className="muted">No stable defaults.</p>;
  }

  return (
    <div className="kv-grid">
      <div className="kv-item">
        <span className="kv-key">analysis_template</span>
        <span className="kv-value">{defaults.analysis_template ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">root_depth_factor</span>
        <span className="kv-value">{formatValue(defaults.root_depth_factor)}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">pawc_factor</span>
        <span className="kv-value">{formatValue(defaults.pawc_factor)}</span>
      </div>
    </div>
  );
}

function RuntimeAssertionList({
  assertions,
}: {
  assertions?: TaskManifestResponse["runtime_assertions"];
}) {
  if (!assertions || assertions.length === 0) {
    return <p className="muted">No runtime assertions.</p>;
  }

  return (
    <ul className="simple-list">
      {assertions.map((assertion, index) => (
        <li key={`${assertion.name ?? "assertion"}-${index}`}>
          {assertion.name ?? "-"} | id={assertion.assertion_id ?? "-"} | type={assertion.assertion_type ?? "-"} | node={assertion.node_id ?? "-"} | required={formatValue(assertion.required)} | repairable={formatValue(assertion.repairable)} | {assertion.message ?? "-"}
        </li>
      ))}
    </ul>
  );
}

function ResumeTransactionPanel({
  transaction,
}: {
  transaction?: TaskDetailResponse["resume_transaction"] | TaskManifestResponse["resume_transaction"];
}) {
  if (!transaction) {
    return <p className="muted">No resume transaction.</p>;
  }

  return (
    <div className="kv-grid">
      <div className="kv-item"><span className="kv-key">resume_request_id</span><span className="kv-value">{transaction.resume_request_id ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">status</span><span className="kv-value">{transaction.status ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">base_checkpoint_version</span><span className="kv-value">{formatValue(transaction.base_checkpoint_version)}</span></div>
      <div className="kv-item"><span className="kv-key">candidate_checkpoint_version</span><span className="kv-value">{formatValue(transaction.candidate_checkpoint_version)}</span></div>
      <div className="kv-item"><span className="kv-key">candidate_inventory_version</span><span className="kv-value">{formatValue(transaction.candidate_inventory_version)}</span></div>
      <div className="kv-item"><span className="kv-key">candidate_manifest_id</span><span className="kv-value">{transaction.candidate_manifest_id ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">candidate_attempt_no</span><span className="kv-value">{formatValue(transaction.candidate_attempt_no)}</span></div>
      <div className="kv-item"><span className="kv-key">candidate_job_id</span><span className="kv-value">{transaction.candidate_job_id ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">failure_reason</span><span className="kv-value">{transaction.failure_reason ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">updated_at</span><span className="kv-value">{transaction.updated_at ?? "-"}</span></div>
    </div>
  );
}

function CorruptionStatePanel({
  corruption,
  promotionStatus,
}: {
  corruption?: TaskDetailResponse["corruption_state"] | TaskManifestResponse["corruption_state"];
  promotionStatus?: string;
}) {
  return (
    <div className="kv-grid">
      <div className="kv-item"><span className="kv-key">is_corrupted</span><span className="kv-value">{formatValue(corruption?.is_corrupted)}</span></div>
      <div className="kv-item"><span className="kv-key">reason</span><span className="kv-value">{corruption?.reason ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">corrupted_since</span><span className="kv-value">{corruption?.corrupted_since ?? "-"}</span></div>
      <div className="kv-item"><span className="kv-key">promotion_status</span><span className="kv-value">{promotionStatus ?? "-"}</span></div>
    </div>
  );
}

function CapabilityContractList({
  outputs,
}: {
  outputs?: CapabilityOutputItemDto[];
}) {
  if (!outputs || outputs.length === 0) {
    return <p className="muted">No capability output contract.</p>;
  }

  return (
    <ul className="simple-list">
      {outputs.map((output, index) => (
        <li key={`${output.logical_name ?? "output"}-${index}`}>
          {output.logical_name ?? "-"} | {output.artifact_role ?? "-"}
        </li>
      ))}
    </ul>
  );
}

function CapabilityValidationHintList({
  hints,
}: {
  hints?: CapabilityValidationHintDto[];
}) {
  if (!hints || hints.length === 0) {
    return <p className="muted">No validation hints.</p>;
  }

  return (
    <ul className="simple-list">
      {hints.map((hint, index) => (
        <li key={`${hint.role_name ?? "role"}-${index}`}>
          {hint.role_name ?? "-"} | expected_slot_type={hint.expected_slot_type ?? "-"}
        </li>
      ))}
    </ul>
  );
}

function CapabilityRepairHintList({
  hints,
}: {
  hints?: CapabilityRepairHintDto[];
}) {
  if (!hints || hints.length === 0) {
    return <p className="muted">No repair hints.</p>;
  }

  return (
    <ul className="simple-list">
      {hints.map((hint, index) => (
        <li key={`${hint.action_key ?? "repair"}-${index}`}>
          {hint.role_name ?? "-"} | {hint.action_label ?? "-"} [{hint.action_key ?? "-"}]
        </li>
      ))}
    </ul>
  );
}

function ManifestCapabilityFactsPanel({
  facts,
}: {
  facts?: TaskManifestResponse["capability_facts"];
}) {
  if (!facts) {
    return <p className="muted">No capability facts.</p>;
  }

  return (
    <div className="kv-grid">
      <div className="kv-item">
        <span className="kv-key">capability_key</span>
        <span className="kv-value">{facts.capability_key ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">display_name</span>
        <span className="kv-value">{facts.display_name ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">runtime_profile_hint</span>
        <span className="kv-value">{facts.runtime_profile_hint ?? "-"}</span>
      </div>
    </div>
  );
}

function LogicalInputRoleList({
  roles,
}: {
  roles?: TaskManifestResponse["logical_input_roles"];
}) {
  if (!roles || roles.length === 0) {
    return <p className="muted">No logical input roles.</p>;
  }

  return (
    <ul className="simple-list">
      {roles.map((role, index) => (
        <li key={`${role.role_name ?? "role"}-${index}`}>
          {role.role_name ?? "-"} | required={formatValue(role.required)}
        </li>
      ))}
    </ul>
  );
}

function SlotSchemaViewPanel({
  slotSchemaView,
}: {
  slotSchemaView?: TaskManifestResponse["slot_schema_view"];
}) {
  const slots = slotSchemaView?.slots;
  if (!slots || slots.length === 0) {
    return <p className="muted">No slot schema view.</p>;
  }

  return (
    <ul className="simple-list">
      {slots.map((slot, index) => (
        <li key={`${slot.slot_name ?? "slot"}-${index}`}>
          {slot.slot_name ?? "-"} | type={slot.type ?? "-"} | bound_role={slot.bound_role ?? "-"}
        </li>
      ))}
    </ul>
  );
}

function SlotBindingList({
  bindings,
}: {
  bindings?: TaskManifestResponse["slot_bindings"];
}) {
  if (!bindings || bindings.length === 0) {
    return <p className="muted">No slot bindings.</p>;
  }

  return (
    <ul className="simple-list">
      {bindings.map((binding, index) => (
        <li key={`${binding.role_name ?? "role"}-${binding.slot_name ?? "slot"}-${index}`}>
          {binding.role_name ?? "-"} {"->"} {binding.slot_name ?? "-"} | source={binding.source ?? "-"}
        </li>
      ))}
    </ul>
  );
}

function ExecutionGraphPanel({
  graph,
}: {
  graph?: TaskManifestResponse["execution_graph"];
}) {
  if (!graph) {
    return <p className="muted">No execution graph.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-key">node_count</span>
          <span className="kv-value">{formatValue(graph.nodes?.length)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">edge_count</span>
          <span className="kv-value">{formatValue(graph.edges?.length)}</span>
        </div>
      </div>
      <h3>Nodes</h3>
      {graph.nodes && graph.nodes.length > 0 ? (
        <ul className="simple-list">
          {graph.nodes.map((node, index) => (
            <li key={`${node.node_id ?? "node"}-${index}`}>
              {node.node_id ?? "-"} | kind={node.kind ?? "-"}
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted">No execution nodes.</p>
      )}
      <h3>Edges</h3>
      {graph.edges && graph.edges.length > 0 ? (
        <ul className="simple-list">
          {graph.edges.map((edge, index) => (
            <li key={`${edge.join("->")}-${index}`}>{edge.join(" -> ")}</li>
          ))}
        </ul>
      ) : (
        <p className="muted">No execution edges.</p>
      )}
    </>
  );
}

function ArgsDraftPanel({
  argsDraft,
}: {
  argsDraft?: Record<string, unknown> | null;
}) {
  if (!argsDraft || Object.keys(argsDraft).length === 0) {
    return <p className="muted">No args draft available.</p>;
  }

  const entries = Object.entries(argsDraft).sort(([left], [right]) => left.localeCompare(right));

  return (
    <ul className="simple-list">
      {entries.map(([key, value]) => (
        <li key={key}>
          <strong>{key}</strong>
          {" | "}
          {formatValue(value)}
          {" | "}
          {Array.isArray(value) ? "array" : value === null ? "null" : typeof value}
        </li>
      ))}
    </ul>
  );
}

function Pass1SummaryPanel({ summary }: { summary?: TaskDetailResponse["pass1_summary"] }) {
  if (!summary) {
    return <p className="muted">No pass1 summary available.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-key">capability_key</span>
          <span className="kv-value">{summary.capability_key ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">selected_template</span>
          <span className="kv-value">{summary.selected_template ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">logical_input_roles_count</span>
          <span className="kv-value">{formatValue(summary.logical_input_roles_count)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">required_roles_count</span>
          <span className="kv-value">{formatValue(summary.required_roles_count)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">optional_roles_count</span>
          <span className="kv-value">{formatValue(summary.optional_roles_count)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">role_arg_mapping_count</span>
          <span className="kv-value">{formatValue(summary.role_arg_mapping_count)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">slot_schema_view_version</span>
          <span className="kv-value">{summary.slot_schema_view_version ?? "-"}</span>
        </div>
      </div>
      <h3>Stable Defaults</h3>
      <StableDefaultsPanel defaults={summary.stable_defaults} />
    </>
  );
}

function SlotBindingsSummaryPanel({
  summary,
}: {
  summary?: TaskDetailResponse["slot_bindings_summary"];
}) {
  if (!summary) {
    return <p className="muted">No binding summary available.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-key">bound_slots_count</span>
          <span className="kv-value">{formatValue(summary.bound_slots_count)}</span>
        </div>
      </div>
      <h3>Bound Roles</h3>
      <StringList values={summary.bound_role_names} emptyText="No bound roles." />
    </>
  );
}

function ArgsDraftSummaryPanel({
  summary,
}: {
  summary?: TaskDetailResponse["args_draft_summary"];
}) {
  if (!summary) {
    return <p className="muted">No args draft summary available.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-key">param_count</span>
          <span className="kv-value">{formatValue(summary.param_count)}</span>
        </div>
      </div>
      <h3>Param Keys</h3>
      <StringList values={summary.param_keys} emptyText="No param keys." />
    </>
  );
}

function ValidationSummaryPanel({
  summary,
}: {
  summary?: {
    is_valid?: boolean;
    missing_roles?: string[];
    missing_params?: string[];
    error_code?: string;
    invalid_bindings?: string[];
  };
}) {
  if (!summary) {
    return <p className="muted">No validation summary available.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-key">is_valid</span>
          <span className="kv-value">{formatValue(summary.is_valid)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">error_code</span>
          <span className="kv-value">{summary.error_code ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">missing_role_count</span>
          <span className="kv-value">{formatValue(summary.missing_roles?.length)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">missing_param_count</span>
          <span className="kv-value">{formatValue(summary.missing_params?.length)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">invalid_binding_count</span>
          <span className="kv-value">{formatValue(summary.invalid_bindings?.length)}</span>
        </div>
      </div>
      <h3>Missing Roles</h3>
      <StringList values={summary.missing_roles} emptyText="No missing roles." />
      <h3>Missing Params</h3>
      <StringList values={summary.missing_params} emptyText="No missing params." />
      <h3>Invalid Bindings</h3>
      <StringList values={summary.invalid_bindings} emptyText="No invalid bindings." />
    </>
  );
}

function Pass2SummaryPanel({ summary }: { summary?: TaskDetailResponse["pass2_summary"] }) {
  if (!summary) {
    return <p className="muted">No pass2 summary available.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-key">planner</span>
          <span className="kv-value">{summary.planner ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">capability_key</span>
          <span className="kv-value">{summary.capability_key ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">template</span>
          <span className="kv-value">{summary.template ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">node_count</span>
          <span className="kv-value">{formatValue(summary.node_count)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">edge_count</span>
          <span className="kv-value">{formatValue(summary.edge_count)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">validation_is_valid</span>
          <span className="kv-value">{formatValue(summary.validation_is_valid)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">validation_error_code</span>
          <span className="kv-value">{summary.validation_error_code ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">runtime_assertion_count</span>
          <span className="kv-value">{formatValue(summary.runtime_assertion_count)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">graph_digest</span>
          <span className="kv-value">{summary.graph_digest ?? "-"}</span>
        </div>
      </div>
      <h3>Canonicalization Summary</h3>
      <DebugJsonPanel title="Canonicalization Summary" payload={summary.canonicalization_summary ?? null} defaultExpanded={false} />
      <h3>Rewrite Summary</h3>
      <DebugJsonPanel title="Rewrite Summary" payload={summary.rewrite_summary ?? null} defaultExpanded={false} />
    </>
  );
}

function ResultBundleSummaryPanel({
  summary,
}: {
  summary?: TaskDetailResponse["result_bundle_summary"];
}) {
  if (!summary) {
    return <p className="muted">No result bundle summary.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-key">result_id</span>
          <span className="kv-value">{summary.result_id ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">summary</span>
          <span className="kv-value">{summary.summary ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">main_output_count</span>
          <span className="kv-value">{formatValue(summary.main_output_count)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">created_at</span>
          <span className="kv-value">{summary.created_at ?? "-"}</span>
        </div>
      </div>
      <h3>Main Outputs</h3>
      <StringList values={summary.main_outputs} emptyText="No main outputs." />
      <h3>Primary Outputs</h3>
      <StringList values={summary.primary_outputs} emptyText="No primary outputs." />
      <h3>Audit Artifacts</h3>
      <StringList values={summary.audit_artifacts} emptyText="No audit artifacts." />
    </>
  );
}

function ResultObjectSummaryPanel({
  summary,
}: {
  summary?: TaskDetailResponse["result_object_summary"];
}) {
  if (!summary) {
    return <p className="muted">No result object summary.</p>;
  }

  return (
    <div className="kv-grid">
      <div className="kv-item">
        <span className="kv-key">result_id</span>
        <span className="kv-value">{summary.result_id ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">summary</span>
        <span className="kv-value">{summary.summary ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">artifact_count</span>
        <span className="kv-value">{formatValue(summary.artifact_count)}</span>
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

function FinalExplanationSummaryPanel({
  summary,
}: {
  summary?: TaskDetailResponse["final_explanation_summary"];
}) {
  if (!summary) {
    return <p className="muted">No final explanation summary.</p>;
  }

  return (
    <div className="kv-grid">
      <div className="kv-item">
        <span className="kv-key">available</span>
        <span className="kv-value">{formatValue(summary.available)}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">title</span>
        <span className="kv-value">{summary.title ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">highlight_count</span>
        <span className="kv-value">{formatValue(summary.highlight_count)}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">generated_at</span>
        <span className="kv-value">{summary.generated_at ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">failure_code</span>
        <span className="kv-value">{summary.failure_code ?? "-"}</span>
      </div>
      <div className="kv-item">
        <span className="kv-key">failure_message</span>
        <span className="kv-value">{summary.failure_message ?? "-"}</span>
      </div>
    </div>
  );
}

function FailureSummaryPanel({
  summary,
}: {
  summary?: TaskDetailResponse["last_failure_summary"];
}) {
  if (!summary) {
    return <p className="muted">No failure summary.</p>;
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
    </div>
  );
}

function MissingSlotList({
  slots,
}: {
  slots?: MissingSlotDto[];
}) {
  if (!slots || slots.length === 0) {
    return <p className="muted">No missing slots.</p>;
  }

  return (
    <ul className="simple-list">
      {slots.map((slot, index) => (
        <li key={`${slot.slot_name}-${index}`}>
          {slot.slot_name} | expected_type={slot.expected_type ?? "-"} | required={formatValue(slot.required)}
        </li>
      ))}
    </ul>
  );
}

function RequiredUserActionList({
  actions,
}: {
  actions?: RequiredUserActionDto[];
}) {
  if (!actions || actions.length === 0) {
    return <p className="muted">No required user actions.</p>;
  }

  return (
    <ul className="simple-list">
      {actions.map((action, index) => (
        <li key={`${action.key}-${index}`}>
          {action.label} [{action.key}] | action_type={action.action_type} | required={formatValue(action.required)}
        </li>
      ))}
    </ul>
  );
}

function WaitingContextPanel({
  waitingContext,
}: {
  waitingContext?: TaskDetailResponse["waiting_context"];
}) {
  if (!waitingContext) {
    return <p className="muted">No waiting context.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-key">waiting_reason_type</span>
          <span className="kv-value">{waitingContext.waiting_reason_type ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">can_resume</span>
          <span className="kv-value">{formatValue(waitingContext.can_resume)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">resume_hint</span>
          <span className="kv-value">{waitingContext.resume_hint ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">missing_slot_count</span>
          <span className="kv-value">{formatValue(waitingContext.missing_slots?.length)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">invalid_binding_count</span>
          <span className="kv-value">{formatValue(waitingContext.invalid_bindings?.length)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">required_action_count</span>
          <span className="kv-value">{formatValue(waitingContext.required_user_actions?.length)}</span>
        </div>
      </div>
      <h3>Missing Slots</h3>
      <MissingSlotList slots={waitingContext.missing_slots} />
      <h3>Invalid Bindings</h3>
      <StringList values={waitingContext.invalid_bindings} emptyText="No invalid bindings." />
      <h3>Required User Actions</h3>
      <RequiredUserActionList actions={waitingContext.required_user_actions} />
    </>
  );
}

function RepairActionExplanationList({
  explanations,
}: {
  explanations?: RepairActionExplanationDto[];
}) {
  if (!explanations || explanations.length === 0) {
    return <p className="muted">No repair action explanations.</p>;
  }

  return (
    <ul className="simple-list">
      {explanations.map((item, index) => (
        <li key={`${item.key}-${index}`}>
          {item.key} | {item.message}
        </li>
      ))}
    </ul>
  );
}

function RepairProposalPanel({
  proposal,
}: {
  proposal?: TaskDetailResponse["repair_proposal"];
}) {
  if (!proposal) {
    return <p className="muted">No repair proposal available.</p>;
  }

  return (
    <>
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-key">available</span>
          <span className="kv-value">{formatValue(proposal.available)}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">user_facing_reason</span>
          <span className="kv-value llm-text">{proposal.user_facing_reason ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">resume_hint</span>
          <span className="kv-value llm-text">{proposal.resume_hint ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">failure_code</span>
          <span className="kv-value">{proposal.failure_code ?? "-"}</span>
        </div>
        <div className="kv-item">
          <span className="kv-key">failure_message</span>
          <span className="kv-value">{proposal.failure_message ?? "-"}</span>
        </div>
      </div>
      <h3>Action Explanations</h3>
      <RepairActionExplanationList explanations={proposal.action_explanations} />
      <h3>Notes</h3>
      <StringList values={proposal.notes} emptyText="No repair notes." />
    </>
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
  const [forceReverting, setForceReverting] = useState(false);
  const [logicalSlot, setLogicalSlot] = useState("");
  const [userNote, setUserNote] = useState("");
  const [selectedCaseId, setSelectedCaseId] = useState("");
  const [currentUserRole, setCurrentUserRole] = useState<string | null>(null);
  const [targetCheckpointVersion, setTargetCheckpointVersion] = useState("");

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
    if (!selectedCaseId && taskResponse.case_projection?.selected_case_id) {
      setSelectedCaseId(taskResponse.case_projection.selected_case_id);
    }
  }, [logicalSlot, selectedCaseId, taskId]);

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

  useEffect(() => {
    let cancelled = false;
    if (!getAccessToken()) {
      return;
    }
    void getMe()
      .then((response) => {
        if (!cancelled) {
          setCurrentUserRole(response.role ?? null);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setCurrentUserRole(null);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [taskId]);

  const canCancel = task?.state === "QUEUED" || task?.state === "RUNNING";
  const clarifyCaseSelection = hasClarifyCaseSelectionAction(task?.waiting_context);
  const canResume = task?.waiting_context?.can_resume === true;
  const canSubmitClarify = clarifyCaseSelection && selectedCaseId.trim().length > 0;
  const canSubmitResume = canResume || canSubmitClarify;
  const canForceRevert = task?.state === "STATE_CORRUPTED" && currentUserRole === "ADMIN";

  useEffect(() => {
    if (
      task?.state === "STATE_CORRUPTED"
      && task.checkpoint_version !== null
      && task.checkpoint_version !== undefined
      && !targetCheckpointVersion
    ) {
      setTargetCheckpointVersion(String(Math.max(task.checkpoint_version - 1, 0)));
    }
  }, [targetCheckpointVersion, task?.checkpoint_version, task?.state]);

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
        args_overrides: selectedCaseId.trim() ? { case_id: selectedCaseId.trim() } : undefined,
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

  async function handleForceRevert() {
    if (!canForceRevert || forceReverting) {
      return;
    }
    const parsedCheckpointVersion = Number.parseInt(targetCheckpointVersion, 10);
    if (!Number.isInteger(parsedCheckpointVersion) || parsedCheckpointVersion < 0) {
      setError("target checkpoint version must be a non-negative integer");
      return;
    }
    setForceReverting(true);
    setError("Force revert request has been sent...");
    try {
      await forceRevertCheckpoint(taskId, {
        request_id: crypto.randomUUID(),
        target_checkpoint_version: parsedCheckpointVersion,
      });
      await loadData();
      setError(null);
    } catch (forceRevertError) {
      setError(forceRevertError instanceof Error ? forceRevertError.message : "Force revert failed");
    } finally {
      setForceReverting(false);
    }
  }

  return (
    <main className="container page-shell">
      <div className="card hero-card">
        <span className="hero-eyebrow">Governed Task Trace</span>
        <h1>Task Detail</h1>
        <p className="muted">Inspect orchestration, cognition, contract projection, repair state, and execution readiness from one place.</p>
        <div className="hero-meta">
          <span className="meta-chip"><strong>task_id</strong>{taskId}</span>
          <span className="meta-chip"><strong>update_mode</strong>{streamMode}</span>
          <span className="meta-chip"><strong>result_bundle</strong>{task?.latest_result_bundle_id ?? "-"}</span>
          <span className="meta-chip"><strong>workspace</strong>{task?.latest_workspace_id ?? "-"}</span>
        </div>
        {loading ? <p>Loading...</p> : null}
        {error ? <p className="error">{error}</p> : null}

        {task ? (
          <div className="hero-status-row">
            <span className="status">{task.state}</span>
            <span className="meta-chip"><strong>state_version</strong>{task.state_version}</span>
            <span className="meta-chip"><strong>input_chain_status</strong>{task.input_chain_status ?? "-"}</span>
          </div>
        ) : null}

      <div className="action-row">
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

      <div className="page-grid">
        <div className="section-stack">
          <div className="section-banner">
            <p className="muted">Primary trail: follow authority, projection, trace, and manifest freeze in the order they affect execution.</p>
            <div className="pill-row">
              <span className="soft-pill"><strong>focus</strong>governed path</span>
              <span className="soft-pill"><strong>repair</strong>{task?.state === "WAITING_USER" ? "active" : "idle"}</span>
            </div>
          </div>

      <div className="card">
        <h2>Governance</h2>
        <p className="muted card-intro">Authority and orchestration facts that determine whether the task can keep moving, wait for the user, or stay blocked.</p>
        {task ? (
          <>
            <div className="kv-grid">
              <div className="kv-item"><span className="kv-key">planning_revision</span><span className="kv-value">{formatValue(task.planning_revision)}</span></div>
              <div className="kv-item"><span className="kv-key">checkpoint_version</span><span className="kv-value">{formatValue(task.checkpoint_version)}</span></div>
              <div className="kv-item"><span className="kv-key">graph_digest</span><span className="kv-value">{task.graph_digest ?? "-"}</span></div>
            </div>
            <h3>Resume Transaction</h3>
            <ResumeTransactionPanel transaction={task.resume_transaction} />
            <h3>Corruption / Promotion</h3>
            <CorruptionStatePanel corruption={task.corruption_state} promotionStatus={task.promotion_status} />
            <h3>Cognition Authority</h3>
            <div className="kv-grid">
              <div className="kv-item"><span className="kv-key">planning_intent_status</span><span className="kv-value">{task.planning_intent_status ?? "-"}</span></div>
              <div className="kv-item"><span className="kv-key">binding_status</span><span className="kv-value">{task.binding_status ?? "-"}</span></div>
              <div className="kv-item"><span className="kv-key">cognition_verdict</span><span className="kv-value">{task.cognition_verdict ?? "-"}</span></div>
              <div className="kv-item"><span className="kv-key">assembly_blocked</span><span className="kv-value">{formatValue(task.assembly_blocked)}</span></div>
            </div>
            <h4>Overruled Fields</h4>
            <StringList values={task.overruled_fields} emptyText="No overruled fields." />
            <h4>Blocked Mutations</h4>
            <StringList values={task.blocked_mutations} emptyText="No blocked mutations." />
            {canForceRevert ? (
              <>
                <h3>Admin Recovery</h3>
                <p className="muted">
                  Force-revert realigns authority to a frozen checkpoint after `STATE_CORRUPTED`.
                </p>
                <div className="action-row">
                  <input
                    type="number"
                    min={0}
                    placeholder="target checkpoint version"
                    value={targetCheckpointVersion}
                    onChange={(event) => setTargetCheckpointVersion(event.target.value)}
                  />
                  <button
                    disabled={!targetCheckpointVersion || forceReverting}
                    onClick={handleForceRevert}
                  >
                    {forceReverting ? "Force Reverting..." : "Force Revert Checkpoint"}
                  </button>
                </div>
              </>
            ) : null}
            <h3>Planning Summary</h3>
            <DebugJsonPanel title="Planning Summary" payload={task.planning_summary ?? null} defaultExpanded={false} />
          </>
        ) : (
          <p className="muted">No governance state available.</p>
        )}
      </div>

      <div className="card">
        <h2>Goal Parse</h2>
        <p className="muted card-intro">The normalized user intent and extracted entities before the system commits to a governed case contract.</p>
        <GoalParsePanel summary={task?.goal_parse_summary} />
        <h3>Goal Route Cognition</h3>
        <CognitionMetadataPanel metadata={task?.goal_route_cognition} emptyText="No goal-route cognition metadata." />
      </div>

      <div className="card">
        <h2>Case Projection</h2>
        <p className="muted card-intro">The governed case-level projection that either resolves the request or asks for clarification before execution.</p>
        <CaseProjectionPanel projection={task?.case_projection} />
      </div>

      <div className="card">
        <h2>Skill Route</h2>
        <p className="muted card-intro">Skill and template selection after the request has been interpreted into capability space.</p>
        <SkillRoutePanel summary={task?.skill_route_summary} />
        <h3>PassB Cognition</h3>
        <CognitionMetadataPanel metadata={task?.passb_cognition} emptyText="No passb cognition metadata." />
      </div>

      <div className="card">
        <h2>LLM Trace</h2>
        <p className="muted card-intro">
          These panels show the structured outputs produced by the cognition layer at each stage, not just the provider summary.
        </p>
        <h3>Goal Route Output</h3>
        <GoalRouteReadablePanel
          payload={task?.goal_route_output ?? (manifest?.goal_parse || manifest?.skill_route ? {
            goal_parse: manifest?.goal_parse ?? null,
            skill_route: manifest?.skill_route ?? null,
          } : null)}
        />
        <StageOutputPanel
          title="Goal Route Output"
          payload={task?.goal_route_output ?? (manifest?.goal_parse || manifest?.skill_route ? {
            goal_parse: manifest?.goal_parse ?? null,
            skill_route: manifest?.skill_route ?? null,
          } : null)}
          emptyText="No goal-route output available."
        />
        <h3>PassB Output</h3>
        <PassBReadablePanel
          payload={task?.passb_output ?? (manifest?.slot_bindings || manifest?.args_draft ? {
            slot_bindings: manifest?.slot_bindings ?? [],
            args_draft: manifest?.args_draft ?? null,
          } : null)}
        />
        <StageOutputPanel
          title="PassB Output"
          payload={task?.passb_output ?? (manifest?.slot_bindings || manifest?.args_draft ? {
            slot_bindings: manifest?.slot_bindings ?? [],
            args_draft: manifest?.args_draft ?? null,
          } : null)}
          emptyText="No passb output available."
        />
        <h3>Repair Proposal Output</h3>
        <RepairProposalReadablePanel
          payload={task?.repair_proposal_output ?? (task?.repair_proposal ? task.repair_proposal as Record<string, unknown> : null)}
        />
        <StageOutputPanel
          title="Repair Proposal Output"
          payload={task?.repair_proposal_output ?? (task?.repair_proposal ? task.repair_proposal as Record<string, unknown> : null)}
          emptyText="No repair proposal output available."
        />
        <h3>Final Explanation Output</h3>
        <FinalExplanationReadablePanel payload={task?.final_explanation_output} />
        <StageOutputPanel
          title="Final Explanation Output"
          payload={task?.final_explanation_output}
          emptyText="No final explanation output available yet."
        />
      </div>

      <div className="card">
        <h2>Manifest</h2>
        <p className="muted card-intro">Frozen execution contract, capability facts, bindings, and runtime assertions that are promoted into the runtime layer.</p>
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
              <div className="kv-item">
                <span className="kv-key">capability_key</span>
                <span className="kv-value">{manifest.capability_key ?? "-"}</span>
              </div>
              <div className="kv-item">
                <span className="kv-key">freeze_status</span>
                <span className="kv-value">{manifest.freeze_status ?? "-"}</span>
              </div>
              <div className="kv-item">
                <span className="kv-key">planning_revision</span>
                <span className="kv-value">{formatValue(manifest.planning_revision)}</span>
              </div>
              <div className="kv-item">
                <span className="kv-key">checkpoint_version</span>
                <span className="kv-value">{formatValue(manifest.checkpoint_version)}</span>
              </div>
              <div className="kv-item">
                <span className="kv-key">graph_digest</span>
                <span className="kv-value">{manifest.graph_digest ?? "-"}</span>
              </div>
            </div>
          <h3>Manifest Resume Transaction</h3>
          <ResumeTransactionPanel transaction={manifest.resume_transaction} />
          <h3>Manifest Corruption / Promotion</h3>
          <CorruptionStatePanel corruption={manifest.corruption_state} promotionStatus={manifest.promotion_status} />
          <h3>Manifest Cognition Authority</h3>
          <div className="kv-grid">
            <div className="kv-item"><span className="kv-key">planning_intent_status</span><span className="kv-value">{manifest.planning_intent_status ?? "-"}</span></div>
            <div className="kv-item"><span className="kv-key">binding_status</span><span className="kv-value">{manifest.binding_status ?? "-"}</span></div>
            <div className="kv-item"><span className="kv-key">cognition_verdict</span><span className="kv-value">{manifest.cognition_verdict ?? "-"}</span></div>
            <div className="kv-item"><span className="kv-key">assembly_blocked</span><span className="kv-value">{formatValue(manifest.assembly_blocked)}</span></div>
          </div>
          <h4>Overruled Fields</h4>
          <StringList values={manifest.overruled_fields} emptyText="No overruled fields." />
          <h4>Blocked Mutations</h4>
          <StringList values={manifest.blocked_mutations} emptyText="No blocked mutations." />
          <h3>Manifest Planning Summary</h3>
          <DebugJsonPanel title="Manifest Planning Summary" payload={manifest.planning_summary ?? null} defaultExpanded={false} />
          <h3>Manifest Case Projection</h3>
          <CaseProjectionPanel projection={manifest.case_projection} />
          <h3>Manifest Goal Parse</h3>
          <GoalParsePanel summary={manifest.goal_parse} />
          <h3>Manifest Goal Route Cognition</h3>
          <CognitionMetadataPanel metadata={manifest.goal_route_cognition} emptyText="No manifest goal-route cognition metadata." />
          <h3>Manifest Skill Route</h3>
          <SkillRoutePanel summary={manifest.skill_route} />
          <h3>Manifest PassB Cognition</h3>
          <CognitionMetadataPanel metadata={manifest.passb_cognition} emptyText="No manifest passb cognition metadata." />
          <h3>Capability Facts</h3>
          <ManifestCapabilityFactsPanel facts={manifest.capability_facts} />
          <h3>Capability Output Contract</h3>
          <CapabilityContractList outputs={manifest.capability_facts?.output_contract?.outputs} />
          <h3>Capability Validation Hints</h3>
          <CapabilityValidationHintList hints={manifest.capability_facts?.validation_hints} />
          <h3>Capability Repair Hints</h3>
          <CapabilityRepairHintList hints={manifest.capability_facts?.repair_hints} />
          <h3>Logical Input Roles</h3>
          <LogicalInputRoleList roles={manifest.logical_input_roles} />
          <h3>Slot Schema View</h3>
          <SlotSchemaViewPanel slotSchemaView={manifest.slot_schema_view} />
          <h3>Role Arg Mappings</h3>
          <RoleArgMappingList mappings={manifest.role_arg_mappings} />
          <h3>Stable Defaults</h3>
          <StableDefaultsPanel defaults={manifest.stable_defaults} />
            <h3>Slot Bindings</h3>
            <SlotBindingList bindings={manifest.slot_bindings} />
            <h3>Args Draft</h3>
            <ArgsDraftPanel argsDraft={manifest.args_draft ?? null} />
            <h3>Manifest Validation Summary</h3>
            <ValidationSummaryPanel summary={manifest.validation_summary} />
          <h3>Execution Graph</h3>
          <ExecutionGraphPanel graph={manifest.execution_graph} />
          <h3>Runtime Assertions</h3>
          <RuntimeAssertionList assertions={manifest.runtime_assertions} />
        </>
      ) : (
          <p className="muted">Manifest is not frozen yet.</p>
        )}
      </div>

        </div>

        <aside className="side-rail">
      <div className="card rail-card">
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
            <div className="kv-item">
              <span className="kv-key">provider_key</span>
              <span className="kv-value">{task.job.provider_key ?? "-"}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">capability_key</span>
              <span className="kv-value">{task.job.capability_key ?? "-"}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">runtime_profile</span>
              <span className="kv-value">{task.job.runtime_profile ?? "-"}</span>
            </div>
            <div className="kv-item">
              <span className="kv-key">case_id</span>
              <span className="kv-value">{task.job.case_id ?? "-"}</span>
            </div>
          </div>
        ) : (
          <p className="muted">No job created.</p>
        )}
      </div>

      <div className="card rail-card">
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

      <div className="card rail-card">
        <h2>Pass1 Summary</h2>
        <Pass1SummaryPanel summary={task?.pass1_summary} />
      </div>

      <div className="card rail-card">
        <h2>Bindings</h2>
        <SlotBindingsSummaryPanel summary={task?.slot_bindings_summary} />
      </div>

      <div className="card rail-card">
        <h2>Args Draft</h2>
        <ArgsDraftSummaryPanel summary={task?.args_draft_summary} />
      </div>

      <div className="card rail-card">
        <h2>Validation</h2>
        <ValidationSummaryPanel summary={task?.validation_summary} />
      </div>

      <div className="card rail-card">
        <h2>Pass2 Summary</h2>
        <Pass2SummaryPanel summary={task?.pass2_summary} />
      </div>

      <div className="card rail-card">
        <h2>Result Summaries</h2>
        <h3>Result Object Summary</h3>
        <ResultObjectSummaryPanel summary={task?.result_object_summary} />
        <h3>Result Bundle Summary</h3>
        <ResultBundleSummaryPanel summary={task?.result_bundle_summary} />
        <h3>Final Explanation Summary</h3>
        <FinalExplanationSummaryPanel summary={task?.final_explanation_summary} />
        <h3>Final Explanation Cognition</h3>
        <CognitionMetadataPanel metadata={task?.final_explanation_cognition} emptyText="No final explanation cognition metadata." />
        <h3>Last Failure Summary</h3>
        <FailureSummaryPanel summary={task?.last_failure_summary} />
      </div>

        </aside>
      </div>

      {task?.state === "WAITING_USER" ? (
        <div className="card">
          <h2>Repair Panel</h2>
          <h3>Waiting Context</h3>
          <WaitingContextPanel waitingContext={task.waiting_context} />
          <h3>Repair Proposal</h3>
          <RepairProposalPanel proposal={task.repair_proposal} />
          <h3>Repair Proposal Cognition</h3>
          <CognitionMetadataPanel metadata={task?.repair_proposal_cognition} emptyText="No repair proposal cognition metadata." />
          <div className="action-row">
            <input
              placeholder="logical_slot"
              value={logicalSlot}
              onChange={(event) => setLogicalSlot(event.target.value)}
            />
            <input type="file" onChange={(event) => void handleUpload(event.target.files?.[0] ?? null)} />
          </div>
          <div className="action-row">
            <input
              placeholder="user note"
              value={userNote}
              onChange={(event) => setUserNote(event.target.value)}
            />
            {clarifyCaseSelection ? (
              <select value={selectedCaseId} onChange={(event) => setSelectedCaseId(event.target.value)}>
                <option value="">Select governed case</option>
                {(task.case_projection?.candidate_case_ids ?? []).map((caseId) => (
                  <option key={caseId} value={caseId}>{caseId}</option>
                ))}
              </select>
            ) : null}
            <button disabled={!canSubmitResume || resuming} onClick={handleResume}>
              {resuming ? "Resuming..." : clarifyCaseSelection ? "Submit Clarification" : canResume ? "Resume" : "Resume Blocked"}
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

      <DebugJsonPanel title="Debug JSON" payload={{ task, manifest, events }} />
    </main>
  );
}
