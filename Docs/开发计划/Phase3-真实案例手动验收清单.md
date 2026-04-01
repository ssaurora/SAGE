# Phase3 真实案例手动验收清单

更新日期：2026-04-01

## 1. 目标

本清单用于手动验收 Phase3 前置真实 `water_yield` 案例，并且显式确认以下四个认知环节确实由大模型参与：

- `goal-route`
- `passb`
- `repair proposal`
- `final explanation`

本轮手动验收的通过条件不只是“真实 InVEST 跑通”，还包括：

- real-case 主链没有走 deterministic cognition
- 四个认知阶段的 `provider=glm`
- 四个认知阶段的 `fallback_used=false`

## 2. 验收范围

- `capability_key`: `water_yield`
- `case_id`: `annual_water_yield_gura`
- `provider_key`: `planning-pass1-invest-local`
- `runtime_profile`: `docker-invest-real`

## 3. 环境准备

确认以下条件：

- Docker Desktop 已启动
- Docker Linux engine 可用
- 样例数据存在于 [sample data/Annual_Water_Yield](/e:/paper_project/SAGE/sample%20data/Annual_Water_Yield)

推荐在 PowerShell 中设置：

```powershell
$env:SAGE_SAMPLE_DATA_ROOT_HOST = (Resolve-Path '.\sample data').Path
$env:SAGE_SAMPLE_DATA_ROOT = "/sample-data"
$env:SAGE_INVEST_PIP_SPEC = "natcap.invest"
$env:SAGE_SERVICE_DOCKERFILE = "Dockerfile.invest-real"
$env:SAGE_SERVICE_IMAGE = "sage-pass1-invest-real:compose"
$env:SAGE_SERVICE_RUNTIME_IMAGE = "sage-pass1-invest-real:compose"
$env:SAGE_INVEST_REAL_PRESTART_DELAY_SECONDS = "6"
$env:SAGE_COGNITION_PROVIDER = "glm"
$env:SAGE_COGNITION_GOAL_ROUTE_PROVIDER = "glm"
$env:SAGE_COGNITION_PASSB_PROVIDER = "glm"
$env:SAGE_REPAIR_PROVIDER = "glm"
$env:SAGE_FINAL_EXPLANATION_PROVIDER = "glm"
$env:SAGE_REAL_CASE_LLM_REQUIRED = "true"
```

启动栈：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\compose-up.ps1 -Build
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:8001/health
Invoke-RestMethod http://localhost:8080/actuator/health
```

## 4. 登录

```powershell
$loginBody = @{ username = "demo"; password = "demo123" } | ConvertTo-Json
$loginResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/auth/login" -ContentType "application/json" -Body $loginBody
$token = [string]$loginResponse.access_token
$headers = @{ Authorization = "Bearer $token" }
```

## 5. Success 场景

创建任务：

```powershell
$body = @{ user_query = "run a real case invest annual water yield analysis for gura" } | ConvertTo-Json
$task = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/tasks" -Headers $headers -ContentType "application/json" -Body $body
$taskId = [string]$task.task_id
```

轮询详情直到终态：

```powershell
do {
  Start-Sleep -Seconds 3
  $detail = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/tasks/$taskId" -Headers $headers
  $detail.state
} while ($detail.state -notin @("SUCCEEDED", "FAILED", "WAITING_USER", "STATE_CORRUPTED"))
```

验收点：

- `state = SUCCEEDED`
- `skill_route_summary.execution_mode = real_case_validation`
- `skill_route_summary.provider_preference = planning-pass1-invest-local`

认知验收点：

- `goal_route_cognition.provider = glm`
- `goal_route_cognition.fallback_used = false`
- `passb_cognition.provider = glm`
- `passb_cognition.fallback_used = false`

读取结果：

```powershell
$result = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/tasks/$taskId/result" -Headers $headers
$result | ConvertTo-Json -Depth 12
```

执行验收点：

- `provider_key = planning-pass1-invest-local`
- `runtime_profile = docker-invest-real`
- `case_id = annual_water_yield_gura`
- `docker_runtime_evidence.runtime_mode = invest_real_runner`
- `result_bundle.metrics.used_real_invest = true`

说明验收点：

- `final_explanation_cognition.provider = glm`
- `final_explanation_cognition.fallback_used = false`
- `final_explanation.available = true`

## 6. Repair / Resume 场景

创建缺输入任务：

```powershell
$body = @{ user_query = "run a real case invest annual water yield analysis for gura missing precipitation" } | ConvertTo-Json
$task = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/tasks" -Headers $headers -ContentType "application/json" -Body $body
$repairTaskId = [string]$task.task_id
```

等待进入 `WAITING_USER`：

```powershell
do {
  Start-Sleep -Seconds 3
  $detail = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/tasks/$repairTaskId" -Headers $headers
  $detail.state
} while ($detail.state -notin @("WAITING_USER", "FAILED", "SUCCEEDED"))
```

验收点：

- `state = WAITING_USER`
- `waiting_context.missing_slots` 包含 `precipitation`
- `waiting_context.required_user_actions` 包含 `upload_precipitation`
- `waiting_context.can_resume = false`

认知验收点：

- `goal_route_cognition.provider = glm`
- `goal_route_cognition.fallback_used = false`
- `passb_cognition.provider = glm`
- `passb_cognition.fallback_used = false`
- `repair_proposal_cognition.provider = glm`
- `repair_proposal_cognition.fallback_used = false`

上传缺失文件：

```powershell
$precipitationFile = (Resolve-Path '.\sample data\Annual_Water_Yield\precipitation_gura.tif').Path
curl.exe -s -X POST "http://localhost:8080/tasks/$repairTaskId/attachments" `
  -H "Authorization: Bearer $token" `
  -F "file=@$precipitationFile" `
  -F "logical_slot=precipitation"
```

验收点：

- 返回 `assignment_status = ASSIGNED`

等待 `can_resume=true`，然后调用 `/resume`：

```powershell
do {
  Start-Sleep -Seconds 3
  $detail = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/tasks/$repairTaskId" -Headers $headers
  $detail.waiting_context.can_resume
} while ($detail.state -eq "WAITING_USER" -and $detail.waiting_context.can_resume -ne $true)

$resumeBody = @{ resume_request_id = [guid]::NewGuid().ToString(); user_note = "uploaded real precipitation raster" } | ConvertTo-Json
$resume = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/tasks/$repairTaskId/resume" -Headers $headers -ContentType "application/json" -Body $resumeBody
```

验收点：

- `resume_accepted = true`

最终验收点：

- 最终 `state = SUCCEEDED`
- `result_bundle.metrics.used_real_invest = true`
- `provider_key / runtime_profile / case_id` 与 success 场景一致

## 7. Cancel 场景

创建任务：

```powershell
$body = @{ user_query = "run a real case invest annual water yield analysis for gura" } | ConvertTo-Json
$task = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/tasks" -Headers $headers -ContentType "application/json" -Body $body
$cancelTaskId = [string]$task.task_id
```

等待 `job_state` 进入 `QUEUED` 或 `RUNNING`，然后取消：

```powershell
do {
  Start-Sleep -Seconds 2
  $detail = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/tasks/$cancelTaskId" -Headers $headers
  $jobState = [string]$detail.job.job_state
} while (-not $detail.job -or $jobState -notin @("QUEUED", "RUNNING"))

Invoke-WebRequest -Method Post -Uri "http://localhost:8080/tasks/$cancelTaskId/cancel" -Headers $headers -ContentType "application/json" -Body "{}"
```

验收点：

- HTTP 返回 `202` 或 `409`

最终验收点：

- 最终状态不是 `SUCCEEDED`
- `result.failure_summary` 存在
- `artifacts.logs` 至少有 1 个条目

## 8. 前端核对

详情页 [page.tsx](/e:/paper_project/SAGE/FrontEnd/src/app/tasks/%5BtaskId%5D/page.tsx) 现在应该能直接看到：

- `goal_route_cognition`
- `passb_cognition`
- `repair_proposal_cognition`
- `final_explanation_cognition`

结果页 [page.tsx](/e:/paper_project/SAGE/FrontEnd/src/app/tasks/%5BtaskId%5D/result/page.tsx) 现在应该能直接看到：

- `final_explanation_cognition`
- explanation unavailable 状态

## 9. 判定规则

判定为 `Go`：

- success 通过
- repair / resume 通过
- cancel 通过
- 四个认知阶段都满足 `provider=glm` 且 `fallback_used=false`

判定为 `Conditional Go`：

- 业务链路通过，但真实 GLM 不稳定
- 或执行链通过，但四个认知阶段中有未完成复核的项

判定为 `No-Go`：

- 任一 real-case 认知阶段出现 `provider != glm`
- 任一 real-case 认知阶段出现 `fallback_used = true`
- real-case 主链仍能在 cognition 不满足要求时继续执行

## 10. 清理

```powershell
docker compose --env-file .env.compose down
```
