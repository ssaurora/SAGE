# SAGE Phase0 + Week5 MVP

Current baseline includes both:

- `Phase0`: explicit `goal_parse -> skill_route -> analysis_manifest -> runtime -> result_bundle -> final_explanation`
- `Week5`: minimal repair loop `detect gap -> WAITING_USER -> upload/override -> /resume -> re-enter execution chain`

Week5 goal: deliver the minimal repair loop:

`detect gap -> WAITING_USER -> upload/override -> /resume -> re-enter execution chain`

## Tech boundaries

- `FrontEnd`: Next.js App Router + TypeScript
- `BackEnd`: Spring Boot + MyBatis + Flyway + PostgreSQL + JWT
- `Service/planning-pass1`: FastAPI + Docker runtime manager

## Ports

- FrontEnd: `3000` (or `3100`)
- BackEnd: `8080`
- Service: `8001`
- PostgreSQL: `5432`

## Local startup

### 1) Service

```bash
cd Service/planning-pass1
conda run -n sage-cognitive python -m pip install -r requirements.txt
conda run -n sage-cognitive uvicorn app.main:app --host 0.0.0.0 --port 8001
```

Run tests:

```bash
cd Service/planning-pass1
conda run -n sage-cognitive pytest
```

### 2) BackEnd

```bash
cd BackEnd
mvn spring-boot:run
```

Flyway creates demo user:

- username: `demo`
- password: `demo123`

### 3) FrontEnd

```bash
cd FrontEnd
npm install
npm run dev
```

Open `http://localhost:3000/login`.

## Week5 official APIs

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
- `POST /tasks/{taskId}/cancel`
- `POST /tasks/{taskId}/attachments` (multipart)
- `POST /tasks/{taskId}/resume`

### Service

- `POST /planning/pass1`
- `POST /cognition/passb`
- `POST /validate/primitive`
- `POST /planning/pass2`
- `POST /jobs`
- `GET /jobs/{jobId}`
- `POST /jobs/{jobId}/cancel`

## Deprecated debug aliases (not for acceptance)

- `GET /result?task_id=...`
- `POST /cancel?task_id=...`

## Environment

Backend new env vars:

- `SAGE_UPLOAD_ROOT` (default `BackEnd/runtime/uploads`)
- `SAGE_REPAIR_LLM_ENABLED` (default `true`)
- `SAGE_REPAIR_LLM_API_KEY` (optional; empty uses fallback template)
- `SAGE_REPAIR_LLM_MODEL` (default `gpt-4o-mini`)
- `SAGE_REPAIR_LLM_TIMEOUT_MS` (default `3000`)

## Service Docker

```bash
cd Service/planning-pass1
docker build -t sage-pass1:week5 .
docker run --rm -p 8001:8001 sage-pass1:week5
```

## One-click Week5 E2E

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\week5-e2e.ps1
```

## One-click Docker Compose startup

This repository now supports a single-command local stack using Docker Compose.

### Prerequisites

- Docker Desktop / Docker Engine
- Maven on host PATH (used once to package backend jar before image build)

### Start everything

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\compose-up.ps1 -Build
```

This packages the backend jar, then starts:

- `postgres`
- `redis`
- `service`
- `backend`
- `frontend`

### Default endpoints

- FrontEnd: `http://localhost:3000/login`
- BackEnd: `http://localhost:8080/actuator/health`
- Service: `http://localhost:8001/health`

### GLM (LLM) enablement

The repair proposal endpoint is deterministic by default. To enable GLM-4.7:

1. Open `.env.compose` and set:

```
SAGE_REPAIR_PROVIDER=glm
SAGE_GLM_API_KEY=<your_api_key>
```

2. Restart the stack:

```powershell
docker compose --env-file .env.compose down
powershell -ExecutionPolicy Bypass -File .\\scripts\\compose-up.ps1 -Build
```

### Stop everything

```powershell
docker compose --env-file .env.compose down
```

### Environment file

The compose stack uses:

- `.env.compose`

You can edit ports, container names, and image tags there.

Keep infra running for manual checks:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\week5-e2e.ps1 -KeepRunning
```

The Week5 script verifies:

1. task enters `WAITING_USER`
2. `waiting_context` exists and blocks resume initially
3. attachment upload triggers `waiting_context` refresh
4. `/resume` works with idempotency key
5. task re-enters execution and reaches terminal state
6. repair events exist (`WAITING_USER_ENTERED`, `ATTACHMENT_UPLOADED`, `RESUME_REQUESTED`, `RESUME_ACCEPTED`)

## One-click Phase0 / Week4 E2E

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\week4-e2e.ps1
```

The Week4 script verifies:

1. strict Docker execution path is used
2. task reaches `SUCCEEDED`
3. `goal_parse_summary` and `skill_route_summary` exist on task detail
4. `analysis_manifest` is frozen and queryable from `/tasks/{taskId}/manifest`
5. runtime produces real minimal `water_yield_index` and `climate_balance`
6. `result_bundle`, `final_explanation`, and runtime evidence exist
