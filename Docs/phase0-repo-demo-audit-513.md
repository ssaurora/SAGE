# Phase 0 Repo 与 Demo 审计记录

更新日期：2026-05-13

## 1. 后端 task/job API 审计

后端 `BackEnd` 当前 API 与 README 中记录的主链路基本一致。

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
- `POST /tasks/{taskId}/attachments`
- `POST /tasks/{taskId}/resume`
- `POST /tasks/{taskId}/force-revert-checkpoint`

### Scene / Session

- `GET /scenes`
- `GET /scenes/{sceneId}`
- `GET /scenes/{sceneId}/session`
- `GET /scenes/{sceneId}/session/messages`
- `POST /scenes/{sceneId}/session/messages`
- `POST /scenes/{sceneId}/session/attachments`
- `POST /scenes/{sceneId}/session/demo-live-simulation/reset`
- `POST /scenes/{sceneId}/session/demo-live-simulation/confirm`
- `POST /sessions`
- `GET /sessions`
- `GET /sessions/{sessionId}`
- `GET /sessions/{sessionId}/messages`
- `POST /sessions/{sessionId}/messages`
- `POST /sessions/{sessionId}/attachments`
- `GET /sessions/{sessionId}/stream`

### Compatibility

- `GET /result?task_id=...`
- `POST /cancel?task_id=...`

这两个接口已标记 deprecated，仅用于旧调用兼容。

### Service job API

`Service/planning-pass1` 提供正式 job runtime API：

- `POST /jobs`
- `GET /jobs/{job_id}`
- `POST /jobs/{job_id}/cancel`

并提供 planning / cognition / validation / repair / explanation 相关服务端点。

## 2. 前端 demo-only scenario 与正式绑定边界

`sage-web` 已经有正式后端绑定：

- 场景列表通过 `GET /scenes`。
- 场景详情通过 `GET /scenes/{sceneId}`。
- 场景会话通过 `GET /scenes/{sceneId}/session`。
- 会话消息、附件上传、demo reset/confirm 通过 `api/sessions.ts`。

仍属于 demo-only 的主要入口：

- `src/app/demo/waterYieldDemoScenario.ts`
  - 克隆 water yield base scene 为 success / missing-input 两个 alias。
  - 注入 demo task state、messages、governance 文案、auditTrail、results empty state 和 bindings。
- `src/app/api/scenes.ts`
  - `expandWaterYieldDemoSceneList`
  - `decorateWaterYieldDemoSceneDetail`
  - `decorateWaterYieldDemoSessionProjection`
- `src/app/api/sessions.ts`
  - 对 water yield alias scene 做 canonical scene id 解析。
- `AnalysisSession`
  - 使用 `useDemoLiveSimulationSessionState` 和 `useWaterYieldDemoScenario`。
- `SceneOverview`
  - 使用 demo scenario 覆盖 overview copy / activity / result summary。
- `SceneWorkbench`
  - 使用 demo scenario 切换 workbench mode 与 missing input。
- `TaskGovernance`
  - 使用 demo scenario 构造 synthetic task、validation、lifecycle、audit。
- `SceneResults`
  - missing-input demo 强制隐藏 result items。

结论：正式 task/job 绑定已经存在，但 water yield demo 仍与正式 scene projection 混在主 API adapter 和主页面组件里。Phase 1 应把这些 demo alias 和 decoration 移出正式数据路径。

## 3. 已完成的前端 mock 清理

已清理 `src/app/context/AppContext.tsx` 中的全局默认 mock：

- mock scenes
- mock tasks
- mock results
- mock recent activities
- 默认 current scene / task

现在 AppContext 启动时使用空初始状态，正式页面优先从后端 scene projection 构建视图。`createScene`、`updateTaskState`、`updateInputStatus` 等本地交互函数仍保留，用于后续正式写接口前的局部状态兼容。

同时已为 `src/app/demo/waterYieldDemoScenario.ts` 增加显式开关：

```text
VITE_ENABLE_WATER_YIELD_DEMO_SCENARIOS=true
```

默认不设置该变量时：

- `GET /scenes` 响应不会被扩展为 success / missing-input demo alias。
- `GET /scenes/{sceneId}` 响应不会被 demo scenario 装饰。
- `GET /scenes/{sceneId}/session` 响应不会被 demo waiting/result projection 覆盖。
- 页面组件中的 `useWaterYieldDemoScenario` 不返回 demo scenario。

仍待 Phase 1 继续拆分：

- `waterYieldDemoScenario.ts` 中的 demo messages / governance / results / bindings。
- scene API adapter 对 demo alias 的自动扩展与装饰。
- 页面组件中对 demo scenario 的优先覆盖逻辑。

## 4. 可用于正式执行的 Skill Assets

当前明确可用的正式 skill asset：

### `water_yield`

路径：

```text
Service/planning-pass1/skills/water_yield/
```

已包含：

- `SKILL.md`
- `skill_profile.yaml`
- `model_mapping.yaml`
- `parameter_schema.yaml`
- `validation_policy.yaml`
- `repair_policy.yaml`
- `interpretation_guide.yaml`
- `plan_templates.yaml`
- `mcp_tools_map.yaml`

关键信息：

- `skill_id: water_yield`
- `skill_version: 1.0.0`
- `analysis_type: Annual Water Yield Analysis`
- `selected_template: water_yield_v1`
- supported cases:
  - `annual_water_yield_gura`
  - `annual_water_yield_gtm_national`

必需 roles：

- `watersheds`
- `lulc`
- `biophysical_table`
- `precipitation`
- `eto`

可选 roles：

- `depth_to_root_restricting_layer`
- `plant_available_water_content`

结论：`water_yield` 是当前唯一清晰具备 schema、validation、repair、mapping 和解释指南的正式 skill asset，可以作为 Phase 1-5 的主执行链路基准。

## 5. Phase 1 建议入口

下一步应先拆前端 demo path：

1. 给 demo 扩展增加显式开关，默认正式数据路径不执行 demo scene list expansion / scene detail decoration。
2. 将 water yield success / missing-input demo 从正式 scene list 中移出，作为独立 demo route 或显式 query mode。
3. `Workbench / Governance / Results` 默认只消费后端 `sceneDetail.session_projection.current_task_summary`、`waiting_projection`、`result_summary`、`latest_result_summary`。
4. demo scenario 只在显式 demo mode 下参与 UI 展示。
