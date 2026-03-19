# SAGE Week5 MVP (Phase1 Repair Loop)

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

