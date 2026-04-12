# SAGE

Updated: `2026-04-12`

SAGE is a governed analysis execution system rather than a generic chat application.

The current system goal is:

`natural-language request -> governed planning -> WAITING_USER / resume when inputs are incomplete -> execution -> traceable result / artifact / audit / runtime evidence`

The project is now beyond the original `Phase0 + Week5 MVP` baseline. The active baseline is:

- `Phase0`: explicit `goal_parse -> skill_route -> analysis_manifest -> runtime -> result_bundle -> final_explanation`
- `Week5`: minimal repair loop `detect gap -> WAITING_USER -> upload/override -> /resume -> re-enter execution chain`
- `Week6`: traceability layer `workspace / result_bundle_record / artifact_index / runs / artifacts`
- Current hardening focus: `catalog-first` and `contract-first` governance boundaries

## What SAGE Is Building

SAGE is intended to become a system where:

- the user starts from natural language
- the backend constrains the request into a governed analysis type, case, input-role set, and execution contract
- missing inputs do not silently degrade; they produce a structured `WAITING_USER`
- `/resume` re-enters the formal validation and dispatch path instead of bypassing it
- execution outputs, manifests, audit records, and runtime evidence remain queryable and comparable
- catalog and contract facts become frozen, auditable boundaries instead of incidental fields

The long-term direction is not "more AI everywhere". The long-term direction is stable boundaries:

- frontend renders and interacts
- backend owns workflow, state, freezing, audit, and orchestration
- cognition produces structured suggestions, never workflow authority
- capability assets define schemas and requirements
- execution produces runtime facts
- data layer stores facts and read models without becoming the workflow brain

## Current Project Status

### 1. Governed Main Chain Exists

The following end-to-end backbone is already implemented:

- `POST /tasks`
- controlled routing through `goal_route`, `pass1`, `passB`, validation, and `pass2`
- execution submission
- terminal synchronization from job state back to task state
- result, manifest, artifact, audit, catalog, and contract read APIs

This means the system is no longer a thin wrapper around a single runtime call. There is a real control plane.

### 2. Repair Loop Exists and Is Part of the Main Path

The repair loop is already real and governed:

- incomplete inputs can transition the task into `WAITING_USER`
- `waiting_context_json` is the canonical repair view model
- attachment upload or override can refresh waiting context
- `/resume` is accepted only from `WAITING_USER`
- resume re-enters validation and dispatch instead of skipping them
- repeated resume requests remain idempotent

This is the key difference between a demo chatbot and a workflow system.

### 3. Traceability Layer Exists

Week6 traceability is in place:

- workspace records
- result bundle persistence
- artifact indexing
- attempt history
- run history
- runtime evidence

This gives the system something important for demos and future governance work: outputs are no longer just transient JSON responses.

### 4. `water_yield` Is a Real Skill Asset, Not a Hardcoded Example

`water_yield` has already moved into a proper skill-asset shape with governed metadata and schema gates.

Implemented asset package includes:

- `SKILL.md`
- `skill_profile.yaml`
- `model_mapping.yaml`
- `parameter_schema.yaml`
- `validation_policy.yaml`
- `repair_policy.yaml`
- `interpretation_guide.yaml`
- `plan_templates.yaml`
- `mcp_tools_map.yaml`

Operationally, this already matters:

- `PassB -> parameter_schema -> args_draft` is a hard gate
- schema/binding failure does not silently fall back to legacy behavior
- `skill_id / skill_version` now participate in frozen and projected outputs
- the system has a real governed `water_yield` path instead of a single baked-in sample response

### 5. Catalog-First Slice Is Already in the Main Chain

SAGE does not yet have a full independent metadata catalog platform, but it already has a real `catalog-first` slice that is consumed by the task workflow.

Implemented pieces include:

- `catalog_summary` in waiting/detail/manifest/result views
- catalog consistency projections
- task-level catalog snapshots
- catalog fingerprint / revision semantics in governance views
- frozen manifest persistence of catalog summary
- catalog-aware readiness checks during repair/resume

The important point is that catalog is already more than attachment metadata. It already influences planning, waiting, resume, manifest, and result read models.

### 6. Contract-First Slice Is Already Enforced

Contracts are no longer only descriptive files. They are now part of the execution and governance path.

Currently enforced contracts include:

- `checkpoint_resume_ack`
- `validate_bindings`
- `validate_args`
- `submit_job`
- `cancel_job`
- `query_job_status`
- `collect_result_bundle`
- `index_artifacts`
- `record_audit`

Implemented governance pieces include:

- `contract_version`
- `contract_fingerprint`
- frozen/current contract identity comparison
- structured contract mismatch reporting on resume
- contract governance views in detail/manifest/result/audit/contract query

This means contract can now block or allow the main chain in real code paths.

### 7. Query and Audit Read Models Have Been Unified

Recent backend work focused heavily on pulling read-model assembly out of service-local field stacking and into shared projection/support boundaries.

That work now covers:

- `detail`
- `manifest`
- `result`
- `audit`
- `catalog`
- `contract`

The result is:

- thinner query services
- shared governance semantics for catalog and contract
- fewer duplicated field-assembly branches
- more stable read-model behavior for demos and future refactors

### 8. `TaskService` Has Been Further Reduced Toward Orchestration

Recent refactors continued moving repeated governance facts and projection assembly out of `TaskService`.

Refactored areas now include:

- create-side waiting/failed transitions
- resume-side waiting/failed rollback flows
- audit append helpers
- terminal failure handling
- terminal state transitions
- success-path promotion from `ARTIFACT_PROMOTING -> SUCCEEDED`
- success output summary/event persistence
- accepted-job persistence and submission events
- manifest governance assembly

`TaskService` is not yet a perfectly minimal orchestration shell, but it is materially closer than before.

## What Is Demonstrable Today

Yes, the current repository supports a simple but real demo if the scope stays aligned with what has actually been built.

### Recommended Demo 1: Success Path

Show one governed `water_yield` task that:

1. starts from natural language
2. enters planning
3. gets queued/runs
4. finishes successfully
5. exposes:
   - task detail
   - frozen manifest
   - result bundle
   - artifact/runs views
   - runtime evidence
   - audit trail

### Recommended Demo 2: Repair / Resume Path

Show one task that intentionally starts with missing input:

1. task enters `WAITING_USER`
2. frontend or API shows structured `waiting_context`
3. user uploads attachment or provides override
4. `/resume` is called
5. task re-enters validation / dispatch / execution
6. task continues to terminal state

These two demos already show the core claim of the system:

`governed analysis workflow with repair and traceability`

## Current Limits

The following are intentionally not presented as complete yet:

- full independent catalog lifecycle and catalog domain
- full contract evolution policy and compatibility matrix
- many production-ready skills beyond `water_yield`
- a heavy frontend workbench/product shell

Those are the next-stage problems. They are not the current baseline.

## Repository Boundaries

- `FrontEnd`: Next.js App Router + TypeScript UI
- `BackEnd`: Spring Boot + MyBatis + Flyway + PostgreSQL + JWT
- `Service`: Python/FastAPI planning and execution-adjacent services
- `scripts`: acceptance and demo flows
- `Docs`: planning and architecture documents

## Ports

- FrontEnd: `3000` (or `3100`)
- BackEnd: `8080`
- Service: `8001`
- PostgreSQL: `5432`

## Current Task APIs

### Auth

- `POST /auth/login`
- `GET /auth/me`

### Task

- `POST /tasks`
- `GET /tasks/{taskId}`
- `GET /tasks/{taskId}/events`
- `GET /tasks/{taskId}/stream`
- `GET /tasks/{taskId}/manifest`
- `GET /tasks/{taskId}/result`
- `GET /tasks/{taskId}/runs`
- `GET /tasks/{taskId}/artifacts`
- `GET /tasks/{taskId}/audit`
- `GET /tasks/{taskId}/catalog`
- `GET /tasks/{taskId}/contract`
- `POST /tasks/{taskId}/cancel`
- `POST /tasks/{taskId}/attachments` (multipart)
- `POST /tasks/{taskId}/resume`
- `POST /tasks/{taskId}/force-revert-checkpoint`

### Service

- `POST /planning/pass1`
- `POST /cognition/passb`
- `POST /validate/primitive`
- `POST /planning/pass2`
- `POST /jobs`
- `GET /jobs/{jobId}`
- `POST /jobs/{jobId}/cancel`

### Deprecated Debug Aliases

- `GET /result?task_id=...`
- `POST /cancel?task_id=...`

## Local Startup

### Recommended: Docker Compose

Prerequisites:

- Docker Desktop / Docker Engine
- Maven on host PATH

Start everything:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\compose-up.ps1 -Build
```

This packages the backend jar, then starts:

- `postgres`
- `redis`
- `service`
- `backend`
- `frontend`

Default endpoints:

- FrontEnd: `http://localhost:3000/login`
- BackEnd health: `http://localhost:8080/actuator/health`
- Service health: `http://localhost:8001/health`

Stop everything:

```powershell
docker compose --env-file .env.compose down
```

The compose stack uses `.env.compose` for ports, container names, image tags, and provider settings.

### Manual Startup

#### 1) Service

```bash
cd Service/planning-pass1
conda run -n sage-cognitive python -m pip install -r requirements.txt
conda run -n sage-cognitive uvicorn app.main:app --host 0.0.0.0 --port 8001
```

Service tests:

```bash
cd Service/planning-pass1
conda run -n sage-cognitive pytest
```

#### 2) BackEnd

```bash
cd BackEnd
mvn spring-boot:run
```

Flyway creates a demo user:

- username: `demo`
- password: `demo123`

#### 3) FrontEnd

```bash
cd FrontEnd
npm install
npm run dev
```

Open `http://localhost:3000/login`.

## Environment

Backend env vars currently used by the local stack include:

- `SAGE_UPLOAD_ROOT` (default `BackEnd/runtime/uploads`)
- `SAGE_REPAIR_LLM_ENABLED` (default `true`)
- `SAGE_REPAIR_LLM_API_KEY` (optional; empty uses fallback template)
- `SAGE_REPAIR_LLM_MODEL` (default `gpt-4o-mini`)
- `SAGE_REPAIR_LLM_TIMEOUT_MS` (default `3000`)

### GLM Enablement

The repair proposal path is deterministic by default. To enable GLM:

1. Edit `.env.compose`
2. Set:

```env
SAGE_REPAIR_PROVIDER=glm
SAGE_GLM_API_KEY=<your_api_key>
```

3. Restart the compose stack

```powershell
docker compose --env-file .env.compose down
powershell -ExecutionPolicy Bypass -File .\scripts\compose-up.ps1 -Build
```

## E2E and Demo Scripts

### Week6 Traceability E2E

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\week6-e2e.ps1
```

This script is intended to validate the Week6 baseline around:

- repair/resume chain
- backend/service health
- execution completion
- trace and artifact persistence

Keep the stack/processes running for manual inspection:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\week6-e2e.ps1 -KeepRunning
```

### Real-Case E2E

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase3-realcase-e2e.ps1
```

Supported scenario switch:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase3-realcase-e2e.ps1 -Scenario Success
```

Available scenario values:

- `All`
- `Success`
- `CaseBSuccess`
- `RepairResume`
- `Cancel`
- `Clarify`

This script is the best starting point for a short demo rehearsal because it exercises:

- a successful governed task
- a real-case path
- repair/resume behavior
- cancel and clarify branches

### Legacy Acceptance Scripts

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\week5-e2e.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\week4-e2e.ps1
```

These are still useful for regression checks on earlier milestones.

## Architecture Notes

The project follows a strict layered ownership model:

- frontend does not decide workflow semantics
- backend owns workflow state, orchestration, freezing, and audit
- cognition suggests; it does not own task-state authority
- capability assets define requirements and schemas
- execution produces runtime facts, not business workflow decisions
- read models are projections, not the source of truth

That boundary discipline is central to the current stage of work.

## Near-Term Next Steps

The next development stage should not prioritize broadening the product surface. It should prioritize deepening the current governed boundaries.

Immediate next-stage priorities:

- make catalog a more formal domain with stronger independent lifecycle/query semantics
- continue deepening contract governance and compatibility policy
- keep shrinking orchestration-local fact assembly where it is still duplicated
- preserve the current success/repair/traceability baseline while broadening capability later

In short:

The current system is already strong enough to demonstrate a governed analysis workflow. The next stage is to make its catalog and contract boundaries more formal, more comparable, and more auditable.
