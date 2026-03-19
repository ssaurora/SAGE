# SAGE Agent 行为规范（当前基线：Week5 修复闭环）

本文件约束本仓库内代理实现口径。当前目标是完成 Phase1 最小修复闭环：

`识别缺口 -> WAITING_USER -> 用户补传/修正 -> /resume -> 重入执行链`

## 1. 目录边界
- 前端代码只写在 `FrontEnd`
- 后端代码只写在 `BackEnd`
- Python 代码只写在 `Service`

## 2. Week5 In Scope
- 新状态：`WAITING_USER`
- Repair Dispatcher（唯一裁决）
- `POST /tasks/{taskId}/attachments`
- `POST /tasks/{taskId}/resume`
- `waiting_context` 结构化展示
- `repair_proposal`（LLM 增强层，允许降级）
- 多 attempts + 单 active attempt

## 3. Week5 Out Scope
- Pass1/PassB/Validation/Pass2 主裁决 LLM 化
- explanation 主链 LLM 化
- MinIO/S3 正式化
- retry/rerun/resubmit 全量治理
- 多轮复杂对话修复

## 4. 强制约束

### 4.1 裁决权边界
- Dispatcher 独占输出：`severity/routing/can_resume/required_user_actions`
- LLM 仅输出：`user_facing_reason/resume_hint/action_explanations/notes`
- LLM 不得驱动状态迁移，不得绕过 Validation

### 4.2 waiting_context 定义
- `waiting_context_json` 是当前 `WAITING_USER` 阶段的 canonical repair view model
- 它由 Dispatcher 从底层事实派生，不是底层事实本体

### 4.3 attempt 激活规则
- 每个 task 最多一个 active attempt
- 新 attempt 仅在 task 创建 accepted 或 `/resume` accepted 时激活
- 新 attempt 激活时，旧 active attempt 立即失活
- attempt 终结：`SUCCEEDED | FAILED | CANCELLED | WAITING_USER`

### 4.4 修复视图刷新规则
- 任一成功附件上传或 accepted override 后，必须先重算 `waiting_context`
- 必须基于最新 `waiting_context` 决定 `can_resume`

### 4.5 状态权威
- 状态权威：`task_state + job_record`
- `task_attempt.status_snapshot_json` 仅归档/调试，不是独立状态权威

### 4.6 LLM 输出约束
- `repair_proposal_json` 必须是固定 schema
- LLM 超时/失败必须自动降级模板文案，不阻塞主链

## 5. 环境约束
- Python 本地运行：
  - `conda run -n sage-cognitive uvicorn app.main:app --host 0.0.0.0 --port 8001`
  - `conda run -n sage-cognitive pytest`

## 6. 冲突优先级
1) 用户当轮明确指令
2) 系统/开发者指令
3) 本文档（`AGENT.md`）
4) 现有实现习惯

