# Phase3 真实案例输出与 Artifact 映射说明

更新日期：2026-03-31

## 1. 目标

本说明定义当前真实 `water_yield` 案例在成功、失败、取消后应如何进入现有：

- `result_bundle`
- `artifact_catalog`
- `workspace_summary`
- `docker_runtime_evidence`

本说明强调逻辑分类，而不是绑定某个 provider 的固定物理文件名。

## 2. authority facts

真实案例结果页与详情页当前必须能稳定看到以下 authority facts：

- `provider_key`
- `runtime_profile`
- `case_id`
- `docker_runtime_evidence.input_bindings`

这些字段当前来源于 backend typed projection，不依赖前端自行推断。

## 3. 当前逻辑输出

当前 capability contract 已固定以下逻辑输出名：

Primary Outputs：

- `watershed_results`
- `water_yield_raster`
- `aet_raster`

Audit Artifacts：

- `run_manifest`
- `runtime_request`

Logs：

- `runtime.log`

## 4. 当前落盘与映射

### 4.1 Result Bundle

当前成功路径会固定写出：

- `result_bundle.json`
- `runtime_result.json`
- `metrics.json`
- `watershed_summary.json`
- `watershed_results.json`
- `water_yield_raster.json`
- `aet_raster.json`
- `run_manifest.json`
- `runtime_request.json`

说明：

- `watershed_results.json / water_yield_raster.json / aet_raster.json` 是逻辑输出包装件，用于把 contract 输出稳定接入现有主链。
- 真实 provider 物理产物仍保留在 archive 中，并通过 `output_registry`、`primary_output_refs`、`artifact_catalog` 暴露。

### 4.2 Artifact Catalog Buckets

当前 bucket 约定如下：

- `primary_outputs`
  - 结果包装件与 contract primary output JSON
- `intermediate_outputs`
  - 当前固定包含 `metrics.json`
- `audit_artifacts`
  - `run_manifest.json`
  - `runtime_request.json`
- `derived_outputs`
  - archive 中非保留名的实际 GIS/表格产物
- `logs`
  - `logs/runtime.log`

### 4.3 Real provider 物理输出

真实 InVEST provider 的物理输出名允许变化，但 extractor/展示层当前依赖以下规则：

- 主链语义优先看逻辑输出名和 `artifact_catalog`
- provider 实际输出路径通过 `output_registry` 和 `run_manifest.provider_input_bindings` 追踪
- 不允许前端或 extractor 依赖单 case 特判硬编码

## 5. Anti-cheat 对照点

真实案例验收时必须比对以下事实：

- `result.provider_key` == `run_manifest.provider_key`
- `result.runtime_profile` == `run_manifest.runtime_profile`
- `result.case_id` == `run_manifest.case_id`
- `result.provider_key` == `runtime_request.provider_key`
- `result.runtime_profile` == `runtime_request.runtime_profile`
- `result.case_id` == `runtime_request.case_id`
- `docker_runtime_evidence.input_bindings` 数量与 `run_manifest.provider_input_bindings` 一致
- `docker_runtime_evidence.input_bindings` 数量与 `runtime_request.provider_input_bindings` 一致

这些检查当前已纳入：

- [scripts/phase3-realcase-e2e.ps1](/e:/paper_project/SAGE/scripts/phase3-realcase-e2e.ps1)

## 6. 前端展示入口

当前真实案例输出相关展示入口为：

- 详情页：任务运行状态与 WAITING_USER repair 视图
- 结果页：`provider_key / runtime_profile / case_id / primary_output_refs / input_bindings / artifact buckets`
- Artifact 页：按 bucket 展示各 attempt 的产物

对应文件：

- [FrontEnd/src/app/tasks/[taskId]/result/page.tsx](/e:/paper_project/SAGE/FrontEnd/src/app/tasks/%5BtaskId%5D/result/page.tsx)
- [FrontEnd/src/app/tasks/[taskId]/artifacts/page.tsx](/e:/paper_project/SAGE/FrontEnd/src/app/tasks/%5BtaskId%5D/artifacts/page.tsx)
