# SAGE Agent Guidelines

_Current baseline: Phase1 Week6 (repair loop + traceability layer established)_

This document defines the implementation boundaries for agents and automation working in this repository.
The current goal is not to spread more "intelligence" across the system, but to preserve a stable baseline built on top of `Phase0 + Week5 + Week6`:

`task orchestration -> WAITING_USER / resume -> execution -> traceable results / artifacts / run history`

---

## 1. Directory Boundaries

- Frontend code belongs only in `FrontEnd`
- Java backend code belongs only in `BackEnd`
- Python service / runtime code belongs only in `Service`
- Scripts and acceptance flows belong only in `scripts`
- Architecture and planning documents belong only in `Docs`

---

## 2. Current Scope Baseline

### Completed Baseline

- The minimal `Phase0` executable core exists
- The `Week5` repair loop exists: `WAITING_USER -> upload/fix -> /resume -> re-enter execution`
- The `Week6` traceability layer exists: `workspace / result_bundle_record / artifact_index / runs / artifacts`

### Current In Scope

- Extend fields, APIs, projections, artifacts, and visualizations without violating architecture boundaries
- Fix bugs in state machines, scheduling, artifact indexing, and runtime coordination
- Improve separation between control, cognition, execution, and data ownership
- Prepare clean boundaries for future cognition-service extraction, provider abstraction, and capability expansion

### Current Out of Scope

- Do not introduce multi-skill orchestration complexity
- Do not push business workflow decisions into frontend or execution layers
- Do not spread prompt / provider / model logic into the Java control layer
- Do not bypass Dispatcher / Validation / Pass2 with shortcuts
- Do not treat read models as a new source of truth

---

## 3. Hard Constraints

### 3.1 Dispatcher Authority

Only Dispatcher / control-layer routing may decide:

- `RECOVERABLE` vs `FATAL`
- `WAITING_USER` vs `FAILED`
- `can_resume`
- `required_user_actions`

LLM output must not decide these values.

### 3.2 `waiting_context` Definition

- `waiting_context_json` is the canonical repair view model for the current `WAITING_USER` state
- It is derived by the control layer from lower-level facts
- Frontend consumes it and must not reconstruct repair semantics on its own

### 3.3 Attempt Rules

- One task may have multiple attempts
- At most one active attempt may exist at a time
- A new attempt may only be created after task creation is accepted or `/resume` is accepted
- Attempt terminal states are limited to: `SUCCEEDED | FAILED | CANCELLED | WAITING_USER`
- Historical attempts are archival/debug records, not current workflow authority

### 3.4 State Ownership

- `job_record` is the source of truth for execution state
- `task_state` is the source of truth for task workflow state
- `repair_proposal_json` is enhancement only, not workflow authority
- `task_attempt.status_snapshot_json` is archival/debug only, not an independent state authority

### 3.5 Resume Boundary

- `/resume` may only be accepted from `WAITING_USER`
- Resume must re-enter the defined validation / dispatcher / pass2 path
- Resume must not bypass Validation or Pass2 preconditions
- Repeated resume requests must remain idempotent
- Any successful attachment upload or accepted override must trigger `waiting_context` re-evaluation before `can_resume` is determined
- Resume must never be accepted against stale `waiting_context` data

### 3.6 LLM Output Constraints

- Any LLM output must conform to an explicit schema
- LLM timeout / failure must degrade automatically and must not block the main path
- LLM output is suggestion / explanation only, never workflow authority

---

## Architecture Boundaries (Six-Layer Rules)

This project follows a strict six-layer architecture.
When modifying code, always preserve layer ownership and do not move responsibilities across layers.

### Layer 1 - Presentation Layer (FrontEnd)

Scope:

- user-facing pages
- task detail view
- result view
- repair / WAITING_USER panels
- upload and resume interactions
- SSE / polling UI updates

Allowed:

- render structured data returned by backend
- trigger backend APIs
- display task/job/result/repair states

Forbidden:

- do not implement business rules here
- do not decide `WAITING_USER` vs `FAILED`
- do not infer resume eligibility in UI
- do not reconstruct missing repair logic from raw fields
- do not call LLM providers directly from frontend

Rule:
Frontend is a display and interaction layer only.
It must consume structured backend facts, not invent workflow decisions.

---

### Layer 2 - Control Layer (BackEnd / Java)

Scope:

- task state machine
- auth / permissions
- idempotency
- API contracts
- orchestration flow
- persistence coordination
- event writing
- task/job projection
- resume / cancel entry handling
- repair routing via Dispatcher

Allowed:

- own `task_state` transitions
- own `event_log` and `audit_record` writing
- own resume acceptance / rejection
- own Dispatcher routing (`WAITING_USER` vs `FAILED`)
- assemble structured context objects for cognition-layer calls
- provide rule-based fallback when cognition layer is unavailable

Forbidden:

- do not own prompt templates
- do not own model selection logic
- do not call OpenAI or other LLM providers directly for business cognition as a long-term architecture
- do not embed provider-specific prompt engineering here
- do not own execution runtime internals
- do not directly mutate execution results outside the defined persistence flow

Rule:
The control layer owns state, workflow, contracts, and persistence coordination.
It may call cognition services, but must not become the cognition layer.

---

### Layer 3 - Cognition Layer (LLM / Reasoning Layer)

Scope:

- prompt templates
- model/provider selection
- structured reasoning outputs
- repair proposal generation
- future Pass1 / PassB / explanation intelligence
- schema-constrained cognitive outputs

Allowed:

- generate `repair_proposal`
- generate user-facing explanations
- generate suggestions under strict schemas
- choose provider/model internally
- apply retry / fallback / degradation inside the cognition service

Forbidden:

- do not own `task_state` transitions
- do not decide `WAITING_USER` vs `FAILED`
- do not decide `can_resume`
- do not bypass Validation or Dispatcher
- do not directly write database state
- do not directly submit jobs
- do not become the source of truth for system state

Rule:
Cognition generates structured suggestions and explanations only.
It is never the authority for workflow state or execution truth.

---

### Layer 4 - Capability Layer (Skill / Tool / Model Capability Abstraction)

Scope:

- skill definitions
- capability registry
- parameter schemas
- tool contracts
- template metadata
- model-specific input requirements
- provider / capability negotiation metadata

Allowed:

- define what a skill/model requires
- define parameter schemas and role schemas
- expose reusable capability metadata
- describe execution prerequisites

Forbidden:

- do not own task orchestration
- do not own runtime job state
- do not contain UI logic
- do not contain provider-specific LLM prompts unless they are explicitly part of cognition assets
- do not directly execute long-running jobs

Rule:
The capability layer defines what can be done and what inputs are required.
It does not decide when or why a task should run.

---

### Layer 5 - Execution Layer (Worker / Runtime Layer)

Scope:

- job execution
- worker lifecycle
- runtime state production
- heartbeats
- result object generation
- cancellation handling
- runtime error generation
- Docker / container / process execution

Allowed:

- own `job_state` lifecycle
- produce runtime facts
- write result objects / failure summaries through defined channels
- perform physical cancellation / process termination
- emit execution-facing status

Forbidden:

- do not own `task_state` transitions
- do not own `WAITING_USER` routing
- do not generate business repair logic
- do not own prompt semantics
- do not directly decide frontend-facing workflow outcomes

Rule:
The execution layer runs jobs and produces runtime facts.
It must not become the workflow authority.

---

### Layer 6 - Data Layer (Persistence / Facts / Read Models)

Scope:

- database schema
- source-of-truth records
- projections / read models
- attachments
- repair records
- attempts
- result persistence
- audit persistence

Allowed:

- persist immutable facts and controlled projections
- separate source-of-truth records from read models
- preserve append-only event history
- store attempt / attachment / repair records

Forbidden:

- do not implement business decisions in schema or triggers unless explicitly designed
- do not let read models become independent truth
- do not duplicate source-of-truth logic across multiple tables without explicit derivation rules

Rule:
The data layer stores facts and projections.
It does not decide workflow semantics.

---

## Cross-Layer Ownership Rules

### Source of Truth

- `job_record` is the source of truth for execution state
- `task_state` is the source of truth for task workflow state
- `waiting_context_json` follows the canonical repair view model defined above
- `repair_proposal_json` is enhancement only, not authority
- frontend must read projections, not infer state independently

### LLM Boundary

- LLM participation belongs to the Cognition Layer
- Java backend must not directly own prompts, provider logic, or model-specific parsing as a long-term architecture
- Any temporary direct model call in backend must be treated as transitional and migrated out later

### Dispatcher Authority

Only Dispatcher may decide:

- `RECOVERABLE` vs `FATAL`
- `WAITING_USER` vs `FAILED`
- `can_resume`
- `required_user_actions`

LLM must not decide these.

### Resume Boundary

- `/resume` is accepted only from `WAITING_USER`
- resume must re-enter through the defined validation path
- resume must not bypass Validation or Pass2 preconditions
- repeated resume requests must be idempotent

### Attempt Boundary

- one task may have multiple attempts
- only one active attempt may exist at a time
- historical attempts are archival/debug records, not current workflow authority

### Result Boundary

- `result_bundle` is the success-path business output
- `failure_summary` is the failure-path structured output
- `docker_runtime_evidence` is top-level runtime evidence only
- execution evidence must not pollute business result semantics

---

## Architecture Safety Rules

When implementing new features, prefer adding a new boundary-respecting component over leaking logic into the wrong layer.

Examples:

- If prompt logic is needed, add it to cognition-layer assets/services, not Java orchestration services
- If workflow routing is needed, add it to Dispatcher or control-layer services, not frontend or worker
- If runtime status is needed, derive it from execution facts, not frontend heuristics
- If a UI needs new fields, extend backend contracts instead of recomputing meaning in frontend

Never solve a missing-layer problem by pushing logic into a neighboring layer.

---

## Docker Build & Compose Rules

These rules define the canonical local container workflow. Follow them unless explicitly overridden.

### Compose and Env Usage

- Use `docker compose --env-file .env.compose` for all stack commands (`up`, `down`, `ps`, `logs`).
- Keep defaults in `docker-compose.yml` via `${VAR:-default}` so manual compose commands never collapse to empty values.
- Prefer the scripted entrypoint `scripts/compose-up.ps1 -Build` as the single one-click startup path.

### Backend Image (Option A)

- Package the backend jar on the host (`mvn -q -DskipTests package`) before building the image.
- Backend Dockerfile must be runtime-only and copy `target/backend-0.0.1-SNAPSHOT.jar`.
- Do not run Maven inside the backend Docker build to avoid slow, brittle dependency fetches.

### Frontend Image

- Always ensure a `public/` directory exists before copying to the runtime stage.
- Keep `.dockerignore` for frontend (`node_modules`, `.next`) to reduce build context size.

### Service Image

- Use `python:3.12-slim` as the base image for the service container.

### Healthchecks

- Healthchecks must only use tools present in the base image.
- Backend uses `curl` (installed in the image) to check `/actuator/health`.
- Do not rely on Python inside the Java runtime image.

### Network Conventions

- Backend talks to postgres/service by Compose service names (`postgres:5432`, `service:8001`).
- Frontend talks to backend via `http://localhost:${SAGE_BACKEND_PORT:-8080}` (browser-facing).

---

## Phase1 Stability Guardrails

Do not introduce the following unless explicitly requested:

- multi-skill orchestration
- multi-provider cognition routing
- broad Pass1 / PassB LLM replacement
- full object-storage migration
- generalized retry / rerun frameworks
- parallel workflow engines

Prefer strengthening the current repair, execution, and traceability baseline over expanding architectural breadth.

---

## Temporary Transitional Rule

If some cognition logic is still temporarily implemented in the Java backend:

- keep it isolated behind a dedicated service/client boundary
- mark it as transitional architecture
- do not spread prompt logic across controllers/services
- do not let temporary implementation become permanent through copy-paste reuse

### Transitional Exception: `RepairProposalService`

Any direct LLM/provider call currently living in the Java backend is a temporary Phase1 implementation only.
Do not expand this pattern.
All future prompt / model / provider logic must move to the Cognition Layer behind a backend client boundary.

---

## Conflict Priority

1. Explicit user instruction in the current turn
2. System / developer instruction
3. This document (`AGENTS.md`)
4. Existing repository conventions
