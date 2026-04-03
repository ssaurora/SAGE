# Phase3-B Week 2 开发记录
更新日期：2026-04-03

## 0. 文档目的

本文用于记录 `Phase3-B / Week 2` 的实际开发结果，并对照 Week 2 计划判断：

- `water_yield` 是否已经从“代码知识”推进到“真实 skill 资产”
- `PassB -> parameter_schema -> args_draft` 是否已经成为真实硬 Gate
- skill 资产是否已经进入 route / validation / repair / explanation 的真实运行路径
- Week 2 是否已经具备从 `Conditional Go` 升级为正式通过的证据

本文只记录已经落地并被验证的结果，不把“方向正确”误写成“已经完成”。

---

## 1. Week 2 的唯一成功定义

Week 2 的成功不以“补齐一组 YAML 文件”为准，而以：

> `PassB -> parameter_schema -> args_draft` 成为硬阻断点，且 skill 资产开始进入 route / validation / repair / explanation 的真实运行路径；当 asset/schema 失败时，不允许静默回退到旧 `skill_catalog.py` 继续生成可执行结果。

因此，Week 2 要证明的是“资产掌权”，不是“资产存在”。

---

## 2. 本周实际完成内容

## 2.1 已建立 `water_yield` 的最小 Skill 资产包

本周新增了正式的 `water_yield` skill asset 目录：

- [SKILL.md](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/SKILL.md)
- [skill_profile.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/skill_profile.yaml)
- [model_mapping.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/model_mapping.yaml)
- [parameter_schema.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/parameter_schema.yaml)
- [validation_policy.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/validation_policy.yaml)
- [repair_policy.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/repair_policy.yaml)
- [interpretation_guide.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/interpretation_guide.yaml)
- [plan_templates.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/plan_templates.yaml)
- [mcp_tools_map.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/mcp_tools_map.yaml)

同时新增了 skill asset 加载器：

- [skill_assets.py](/e:/paper_project/SAGE/Service/planning-pass1/app/skill_assets.py)

当前这套资产已经承载：

- `skill_id`
- `skill_version`
- `analysis_type`
- `capability_key`
- `supported_case_ids`
- `required_roles`
- `optional_roles`
- role 到 arg 的映射
- stable defaults
- validation / repair / explanation 的最小规则边界

需要强调的是：

- `skill_profile.yaml` 当前只提供 soft binding hint
- 没有把 provider/runtime 重新固化成 skill 的唯一强绑定
- provider 解析权仍保留在 capability / registry / contract 路径

这满足了 Week 2 “资产化但不越层”的约束。

---

## 2.2 `PassB -> parameter_schema -> args_draft` 已成为硬 Gate

关键改动落在：

- [planner.py](/e:/paper_project/SAGE/Service/planning-pass1/app/planner.py)

当前 `passb` 的可执行 `args_draft` 已经优先由：

- [parameter_schema.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/parameter_schema.yaml)

驱动生成。

已经落地的硬阻断语义为：

- skill asset 未加载
- `parameter_schema` 缺失
- `parameter_schema` 损坏
- schema 与 selected case / bindings / required roles 绑定失败

一旦出现上述任一情况：

- `passb` 不再生成可执行 `args_draft`
- system 返回结构化失败
- backend 不会把该结果误判成成功、clarify 成功或普通降级

本周同时明确落实了最重要的一条防伪资产化规则：

> 当 `parameter_schema` 缺失、损坏或绑定失败时，`passb` 不得静默 fallback 到旧 `skill_catalog.py` 继续生成可执行结果。

这意味着 Week 2 的资产化已经进入真实主链，而不是停留在文件层。

---

## 2.3 `skill_catalog.py` 已退居过渡兼容层

相关文件：

- [skill_catalog.py](/e:/paper_project/SAGE/Service/planning-pass1/app/skill_catalog.py)

Week 2 之后，`skill_catalog.py` 的角色变为：

- asset-first 读取入口的兼容后备
- 非 `PassB` 硬 Gate 场景下的过渡性 legacy definition

但它已经不再承担：

- `parameter_schema` 失败时的可执行 `args_draft` 兜底来源

因此，本周至少已经真实替换掉了一条旧读取路径：

- `args_draft` 的主事实来源从旧 `skill_catalog.py` 迁移到了 `parameter_schema.yaml`

这满足了“Week 2 必须出现至少一条可删除旧逻辑证据”的要求。

---

## 2.4 已形成“硬 Gate + 弱消费扩散”的最小资产掌权闭环

Week 2 的唯一硬 Gate 在 `PassB`，但其他模块并未继续完全躺在旧逻辑里。

当前已经形成的最小弱消费扩散如下。

### A. `goal-route`

相关代码：

- [planner.py](/e:/paper_project/SAGE/Service/planning-pass1/app/planner.py)
- [GoalRouteService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/GoalRouteService.java)

当前已消费的 asset 事实：

- `skill_id`
- `skill_version`
- `analysis_type`
- `capability_key`

`goal-route` 输出已经可以稳定带出：

- `skill_id`
- `skill_version`

### B. `validation`

相关代码：

- [planner.py](/e:/paper_project/SAGE/Service/planning-pass1/app/planner.py)
- [validation_policy.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/validation_policy.yaml)

当前至少已有一个真实判断开始读取：

- `required_runtime_args`
- `forbidden_semantic_keys`

该 policy 仍只提供规则边界，并未取代控制层或规划面的最终裁决。

### C. `repair`

相关代码：

- [repair.py](/e:/paper_project/SAGE/Service/planning-pass1/app/repair.py)
- [repair_policy.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/repair_policy.yaml)

当前 repair proposal 已开始读取：

- `reason_messages`
- `action_message_templates`
- `notes`

该 policy 只参与建议生成，不成为 workflow authority。

### D. `final explanation`

相关代码：

- [explanation.py](/e:/paper_project/SAGE/Service/planning-pass1/app/explanation.py)
- [interpretation_guide.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/interpretation_guide.yaml)

当前 explanation 已开始读取：

- 标题模板
- analysis type 标签
- highlight 模板
- narrative template
- limitation note

同时 explanation 仍受 result bundle 与 task facts 约束，没有脱离执行结果自由发挥。

结论是：

- Week 2 已经不是单点硬 Gate
- 已形成“硬 Gate + 弱消费扩散”的最小资产掌权闭环

---

## 2.5 `skill_id / skill_version` 已进入主链投影，并开始具备冻结语义

关键后端文件包括：

- [Pass1Response.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/planning/dto/Pass1Response.java)
- [Pass1FactHelper.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/planning/Pass1FactHelper.java)
- [CognitionPassBResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/cognition/dto/CognitionPassBResponse.java)
- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)
- [TaskProjectionBuilder.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskProjectionBuilder.java)
- [TaskDetailResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskDetailResponse.java)
- [TaskManifestResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskManifestResponse.java)
- [TaskResultResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskResultResponse.java)

本周已经实现：

- `goal-route / passb` 响应带出 `skill_id / skill_version`
- task detail / manifest / result 投影带出 `skill_id / skill_version`
- 控制层在 `pass1` 后把 `skill_id / skill_version` 冻结回 `skillRouteJson`

同时，本周还落实了一个重要治理点：

> clarify resume 场景优先复用已冻结的 `goal_parse / skill_route / pass1_result`，不会在该路径上重新走 `goal-route / pass1` 并隐式漂移到新的 skill version。

这意味着：

- `skill_version` 已经不只是展示字段
- 它已经开始进入冻结任务的治理语义

需要诚实说明的是：

- Week 2 没有建设复杂的多版本调度平台
- 但“冻结后不隐式漂移”的最小规则已经成立

---

## 2.6 为真实运行链补齐了运行时与 Compose 收口

Week 2 后半段，为了让 asset 真正进入 runtime，又补了三处关键收口：

### A. skill asset 进入 service 镜像

相关文件：

- [Dockerfile](/e:/paper_project/SAGE/Service/planning-pass1/Dockerfile)
- [Dockerfile.invest-real](/e:/paper_project/SAGE/Service/planning-pass1/Dockerfile.invest-real)

修复内容：

- 将 `skills/` 正式打包进 service 镜像
- 解决了 runtime 中 `SKILL_ASSET_UNAVAILABLE` 的真实阻塞

这是 Week 2 从“代码已改”走向“容器运行时真实生效”的关键一步。

### B. Compose 默认值收紧

相关文件：

- [docker-compose.yml](/e:/paper_project/SAGE/docker-compose.yml)

修复内容：

- 将 service build arg `SAGE_INVEST_PIP_SPEC` 的默认值收紧为 `natcap.invest`
- 避免 `Dockerfile.invest-real` 执行空的 `pip install ""`

这符合仓库既有的 compose 默认值约束，也避免了验收过程被环境偶发问题打断。

### C. Case-B E2E 超时窗口收紧为真实运行时长

相关文件：

- [phase3-realcase-e2e.ps1](/e:/paper_project/SAGE/scripts/phase3-realcase-e2e.ps1)

修复内容：

- 将 `CaseBSuccess` 的等待窗口从 `360s` 调整为 `600s`

原因不是放水，而是第二个 GTM real case 的真实执行时长明显长于 `gura`；原脚本会把“慢成功”误判成“未通过”。

---

## 3. 自动化与真实链验证

本周实际执行并通过：

```powershell
python -m compileall Service/planning-pass1/app Service/planning-pass1/tests
```

```powershell
pytest Service/planning-pass1/tests/test_pass1_api.py -q
```

结果：

- `23 passed`

```powershell
mvn -q "-Dtest=TaskProjectionBuilderTest,TaskServiceCognitionFlowTest,TaskServiceGovernanceTest" test
```

本周新增或补强的测试文件包括：

- [TaskProjectionBuilderTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskProjectionBuilderTest.java)
- [TaskServiceCognitionFlowTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceCognitionFlowTest.java)
- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)
- [test_pass1_api.py](/e:/paper_project/SAGE/Service/planning-pass1/tests/test_pass1_api.py)
- [conftest.py](/e:/paper_project/SAGE/Service/planning-pass1/tests/conftest.py)

本轮新增验证点主要覆盖：

- `skill_id / skill_version` 投影
- `passb` asset/schema failure 的结构化失败
- 不得静默 fallback 到旧 `skill_catalog.py`
- clarify resume 复用冻结 skill version
- test helper 与当前 governed contract 的一致性

---

## 4. 真实 E2E 回归结果

本周已实际跑通以下 real-case E2E：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario Success
```

结果：

- `task_20260403024523200_adc38c1c`

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario CaseBSuccess
```

结果：

- `task_20260403031313311_d68088f1`
- `case_id = annual_water_yield_gtm_national`

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario Clarify
```

结果：

- `task_20260403025925211_45b92197`

这意味着 Week 1 打下的双 case governed 主链，在 Week 2 skill asset 化之后继续保持通过。

---

## 5. 对照 Week 2 计划的完成判断

## 5.1 已满足

以下目标已经完成：

- `water_yield` 存在一套真实被运行时消费的 skill asset 包
- `PassB -> parameter_schema -> args_draft` 已成为唯一硬 Gate
- `parameter_schema` 失败时不得静默 fallback 到旧 `skill_catalog.py`
- `goal-route`、`validation`、`repair`、`final explanation` 至少各形成一个真实消费点
- `skill_id / skill_version` 已进入主链输出与任务投影
- clarify resume 已具备最小 skill version 冻结语义
- 至少一条旧 `skill_catalog.py` 读取路径被真实替换
- service 侧测试已实跑通过
- Week 1 的 `Success / CaseBSuccess / Clarify` 真实 E2E 回归已通过

## 5.2 仍然保留但不阻断 Week 2 结论的事项

以下事项仍可继续收紧，但不再构成 Week 2 阻断项：

- 多版本 skill 平台尚未建设
- 前端仍只透传/调试展示 `skill_id / skill_version`，未做更多用户化投影
- `skill_catalog.py` 仍作为过渡兼容层存在，尚未进入可删除状态

这些更适合作为后续 Week 3 / Week 4 的连续收债对象。

---

## 6. 当前阶段结论

本周阶段结论可以正式定性为：

- `Go`

理由如下：

- Week 2 的核心技术目标已经在代码层、service 测试层、backend 测试层和真实 E2E 层全部闭合
- skill asset 已经开始掌权，而不是停留在“文件存在、运行不读”
- `PassB -> parameter_schema -> args_draft` 的硬 Gate 已被真实验证
- Week 1 的双 case governed 主链在 Week 2 修改后继续保持通过

如果需要一句更稳的阶段表述，建议使用：

> 截至 2026-04-03，系统已完成 `water_yield` 的最小 skill 资产化闭环：`PassB -> parameter_schema -> args_draft` 已成为真实硬 Gate，skill asset 已进入 route、validation、repair 与 final explanation 的真实运行路径，并通过 service 单测、backend 单测以及 `Success / CaseBSuccess / Clarify` 三条 real-case E2E 回归完成验证。

---

## 7. 下一步建议

Week 2 之后，最直接的下一步应转入 Week 3：

- 构建最小 Metadata Catalog slice
- 让 catalog-first 开始进入 context / pass2 / resume gate

也就是说：

- Week 1 解决了“case space 是否成立”
- Week 2 解决了“skill 是否开始资产掌权”
- Week 3 要解决“事实源是否开始从附件驱动转向 catalog 驱动”

这也是当前最符合总体设计与偏离评估的推进顺序。
