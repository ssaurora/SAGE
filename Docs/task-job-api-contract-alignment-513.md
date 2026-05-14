# Task / Job API and Contract Alignment

Date: 2026-05-13

This note records the formal frontend/backend boundary used by `sage-web` after the demo-path split.

## Frontend API Imports

`sage-web` should use the following API modules for formal flows:

- `src/app/api/scenes.ts`
  - `GET /scenes`
  - `GET /scenes/{sceneId}`
  - `GET /scenes/{sceneId}/session`
- `src/app/api/sessions.ts`
  - `POST /sessions`
  - `GET /scenes/{sceneId}/session/messages`
  - `POST /scenes/{sceneId}/session/messages`
  - `POST /scenes/{sceneId}/session/attachments`
- `src/app/api/tasks.ts`
  - `GET /tasks/{taskId}`
  - `GET /tasks/{taskId}/manifest`
  - `GET /tasks/{taskId}/audit`
  - `GET /tasks/{taskId}/catalog`
  - `GET /tasks/{taskId}/contract`
  - `POST /tasks/{taskId}/attachments`
  - `POST /tasks/{taskId}/resume`

Formal pages must prefer backend projections over local context:

- Session: `useSceneSessionQuery`, `useSceneSessionMessagesQuery`
- Workbench: `sceneDetail.current_task_id`, `session_projection.current_task_summary`, `waiting_projection`
- Governance: `getTaskGovernanceBundle(taskId)`
- Results: `session_projection.latest_result_summary`, `result_summary`
- Result detail: backend latest result projection first, static annual-water-yield assets only as preview fallback

`AppContext` remains a compatibility shell for legacy pages and route selection. It must not be treated as the authority for formal task, result, governance, or session state.

## Demo Boundary

Water yield demo decorations are feature-flagged by:

```text
VITE_ENABLE_WATER_YIELD_DEMO_SCENARIOS=true
```

Default behavior is off. When the flag is off:

- Demo aliases are not expanded into the scene list.
- Scene/session projections are not decorated.
- Page-level demo scenarios return `null`.
- Formal scene state is fully driven by backend scene/session/task projections.

## Session Boundary

`POST /sessions` creates the formal analysis session and initial task. The frontend should navigate using the returned `scene_id`.

Required request fields:

```json
{
  "title": "Human-readable scene title",
  "user_goal": "Natural-language analysis goal"
}
```

Authoritative response fields:

- `session_id`
- `scene_id`
- `status`
- `current_task_id`
- `current_task_summary`
- `latest_result_summary`
- `waiting_projection`
- `progress_projection`

Scene routes should not fabricate scene ids or task ids locally.

## Task / Job Boundary

The task id is the execution anchor. The frontend resolves it in this order:

1. `sceneDetail.current_task_id`
2. `sceneDetail.session_projection.current_task_id`
3. `sceneDetail.session_projection.current_task_summary.task_id`

Task state must be read from backend projections:

- `TaskDetailResponse.state`
- `SceneDetailDTO.task_state`
- `SessionProjectionDTO.current_task_summary.task_state`

Local state transitions are not authoritative for formal flows.

## Workbench Resume Boundary

Workbench attachment and resume use the task API directly:

1. `POST /tasks/{taskId}/attachments`
2. `POST /tasks/{taskId}/resume`

The backend owns:

- `WAITING_USER` checks
- attachment assignment
- resume idempotency
- validation re-entry
- dispatch / execution re-entry
- rollback to `WAITING_USER` on recoverable validation
- corruption marking on governance mismatch

The frontend only submits user assets and renders the returned task state.

## Catalog-First Boundary

Catalog state is derived from task attachments and inventory metadata.

The catalog projection owns:

- asset count
- ready asset count
- ready role names
- catalog revision
- catalog inventory version
- catalog fingerprint
- catalog coverage consistency

Frontend pages may display catalog facts, but must not infer catalog identity from filenames, labels, or local GIS preview assets.

## Contract-First Boundary

Capability contracts are enforced by backend guards before control-plane actions:

- `checkpoint_resume_ack`
- `validate_bindings`
- `validate_args`
- `submit_job`
- `cancel_job`
- `query_job_status`
- `collect_result_bundle`
- `index_artifacts`
- `record_audit`

Contract identity includes version and fingerprint. Resume must compare frozen and candidate contract identity before validation and execution. Contract drift is a backend governance failure, not a frontend repair decision.

## Manifest Boundary

Manifest snapshots are backend-owned.

Frontend display fields:

- `manifest_version`
- `freeze_status`
- `created_at`
- `validation_summary`
- `contract_governance`

The frontend can show whether a manifest is candidate, frozen, valid, or blocked. It must not freeze or mutate manifests locally.

## Result Boundary

Result pages should use:

- `session_projection.latest_result_summary`
- `result_summary.latest_result_bundle_id`
- `result_summary.latest_result_created_at`
- `result_summary.result_summary_text`

Static annual-water-yield assets are allowed only as a preview fallback for the known demo-compatible scene. They are not result authority.

## Verification

Current verification commands:

```powershell
cd E:\Github\SAGE-ALL\sage-web
pnpm build

cd E:\Github\SAGE-ALL\SAGE\BackEnd
mvn test
```
