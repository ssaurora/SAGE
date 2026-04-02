# Phase3-B Week 1 验收记录

记录日期：2026-04-02

## 0. 文档目的

本文用于记录 `四周推进计划-结合当前代码的修订版` 中 Week 1 的实际完成情况。

本记录只回答四个问题：

- Week 1 的成功定义是什么
- 本周实际完成了哪些代码与数据接入工作
- 是否拿到了足够的自动化与真实运行证据
- 当前是否可以判定 Week 1 通过

---

## 1. Week 1 成功定义

Week 1 的唯一成功标准不是“第二个 case 单独可跑”，而是：

> 第二个真实 `water_yield` case 在不新增关键阶段 case-specific branch 的前提下，复用现有 `clarify -> projection -> validation -> pass2 -> execution -> result/artifact/explanation` 主链，并形成可审计证据。

因此，Week 1 的验收对象不是“第二个样例跑通”，而是“最小 case space 开始成立，且没有复制第一条债务”。

---

## 2. 本次验收对象

本次 Week 1 验收限定在同一 `water_yield` skill 内，不扩展到第二 capability，不扩展到新 runtime，不扩展到多 skill orchestration。

验收范围为：

- `annual_water_yield_gura`
- `annual_water_yield_gtm_national`
- `planning-pass1-invest-local`
- `docker-invest-real`
- clarify / resume / validation / pass2 / execution / result / artifact / explanation 主链

---

## 3. 本周实际完成内容

### 3.1 第二真实 case 已通过 descriptor 接入

新增外部 case descriptor：

- [water_yield_cases.json](/e:/paper_project/SAGE/sample%20data/case-descriptors/water_yield_cases.json)

本次接入的第二真实 case 为：

- `case_id = annual_water_yield_gtm_national`

其数据来源为：

- [README.md](/e:/paper_project/SAGE/sample%20data/IEEM-ES-GTM-main/README.md)

本次选定的 annual water yield 输入组合为：

- `watersheds/gtm_watersheds.shp`
- `watersheds/gtm_subwatersheds.shp`
- `lulc_country/gtm_lulc_2012.tif`
- `model_lookup_tables/annual_water_national_gtm.csv`
- `annual_precipitation/gtm_annual_precipitation.tif`
- `reference_evapotranspiration/gtm_reference_evapotranspiration.tif`
- `depth_to_root_restricting_layer/gtm_depth_to_root_restricting_layer.tif`
- `plant_available_water_content/gtm_plant_available_water_content.tif`

本次接入方式满足 Week 1 约束：

- 通过 case registry / descriptor 接入
- 未新增 Java backend 的第二 case 专属 path assembly
- 未新增第二 case 专属 provider/runtime 分支

### 3.2 Service 侧已形成第二 case 的 governed projection

相关代码：

- [case_registry.py](/e:/paper_project/SAGE/Service/planning-pass1/app/case_registry.py)
- [planner.py](/e:/paper_project/SAGE/Service/planning-pass1/app/planner.py)
- [runtime.py](/e:/paper_project/SAGE/Service/planning-pass1/app/runtime.py)

本周新增或确认的能力包括：

- 支持从外部 registry 文件加载第二个 executable case
- `candidate_case_ids` 只优先面向 executable cases
- ambiguity query 会进入 `clarify_required`
- `args_draft` 由 case descriptor 驱动，不再要求 Java 为第二 case 拼路径
- 增加 case facts 一致性硬校验：
  - `case_projection.selected_case_id`
  - `args_draft.case_id`
  - `case_descriptor_version`
  - `sample_data_root`
  - governed provider input paths

若上述 case facts 不一致，运行将以结构化错误收口，而不会进入成功态。

### 3.3 认知降级链已补稳

本周在认知层内补上了对 GLM 空内容/截断返回的降级处理：

- [planner.py](/e:/paper_project/SAGE/Service/planning-pass1/app/planner.py)
- [explanation.py](/e:/paper_project/SAGE/Service/planning-pass1/app/explanation.py)

同时控制层已收口到接受“合法认知 fallback”，而不是把它一律判成 real-case 违规：

- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)
- [CognitionVerdictResolver.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/CognitionVerdictResolver.java)

这一步的意义是：

- LLM 波动不会再把 clarify 主链直接卡死
- fallback 仍留在 cognition layer 内部处理
- 没有把 authority 下放给前端或执行层

### 3.4 脚本与测试已补到 Week 1 范围

相关文件：

- [phase3-realcase-e2e.ps1](/e:/paper_project/SAGE/scripts/phase3-realcase-e2e.ps1)
- [test_pass1_api.py](/e:/paper_project/SAGE/Service/planning-pass1/tests/test_pass1_api.py)
- [TaskServiceCognitionFlowTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceCognitionFlowTest.java)

本周已补的关键场景：

- `CaseBSuccess`
- `Clarify`
- second executable case discovery
- goal-route/passb fallback acceptance
- case facts mismatch fail-fast

---

## 4. 本次验收证据

## 4.1 静态与单测证据

本次已实际执行并通过：

```powershell
python -m compileall Service/planning-pass1/app/planner.py
mvn -q "-Dtest=TaskServiceCognitionFlowTest" test
```

脚本语法检查已通过：

```powershell
[System.Management.Automation.Language.Parser]::ParseFile(...)
```

## 4.2 真实运行证据

### A. 基线 success

已通过任务：

- `task_20260402134038509_84453cd1`

结论：

- `SUCCEEDED`
- `case_id = annual_water_yield_gura`
- `provider_key = planning-pass1-invest-local`
- `runtime_profile = docker-invest-real`

### B. 第二 case success

已通过任务：

- `task_20260402134132028_28cc9bcc`

结论：

- `SUCCEEDED`
- `case_id = annual_water_yield_gtm_national`
- 第二 case 进入真实 InVEST runtime
- `args_draft` 与 GTM descriptor 一致

### C. Clarify -> resume -> second case success

已通过任务：

- `task_20260402141017241_52d27c91`

结论：

- 初始 query 进入 `WAITING_USER`
- `candidate_case_ids` 同时包含：
  - `annual_water_yield_gura`
  - `annual_water_yield_gtm_national`
- 通过 `/resume` 选择 GTM case
- 最终 `SUCCEEDED`
- 最终 authority facts 为：
  - `provider_key = planning-pass1-invest-local`
  - `runtime_profile = docker-invest-real`
  - `case_id = annual_water_yield_gtm_national`

这条任务证明了：

- clarify 不是装饰行为
- 第二 case 不是只靠 direct query 命中
- 同一套 governed 主链可以承接 ambiguity -> clarify -> resume -> real execution

---

## 5. 与 Week 1 成功定义的对照结论

### 5.1 已满足

- 两个真实 executable case 已存在
- 两个 case 都能从自然语言进入主链
- 第二 case 通过 descriptor 接入，而不是通过新增 Java case-specific branch 接入
- `clarify -> projection -> validation -> pass2 -> execution -> result/artifact/explanation` 主链已在第二 case 上成立
- ambiguity query 已能返回两个 executable case 的 `candidate_case_ids`
- `/resume` 选择第二 case 后可回到真实执行主链
- `case_projection / args_draft / manifest / runtime evidence` 的 case facts 可以对齐

### 5.2 本周收债证据

本周不是只新增了一个 case，还形成了以下“收债型证据”：

- 第二 case 没有通过新增关键阶段主链分支落地
- case 接入事实开始从外部 descriptor 进入系统，而不是继续散落在 Java 控制层
- 认知失败不再只能把主链打断，而是开始通过受治理 fallback 收口

### 5.3 仍然存在但未扩大化的旧债务

以下问题依然存在，但本周没有继续扩大：

- Java control layer 中仍残留部分领域硬编码
- `water_yield` skill 仍未真正资产化
- Metadata Catalog 仍未成为默认事实源

这些应继续列入 Week 2~4，而不是误写成已完成。

---

## 6. 本周禁止动作检查结果

对照 Week 1 的禁止动作，本次验收结论如下：

- 未在 Java backend 中新增第二 case 专属关键词分支
- 未在 Java backend 中新增第二 case 专属 provider 偏好分支
- 未在 Java backend 中新增第二 case 专属路径拼装逻辑
- 未在 Python service 中新增第二 case 专属 resolved shortcut branch
- 未新增第二 case 专属 result extractor / explanation / artifact 特判
- 未通过前端隐藏字段绕过 clarify/resume 正式入口

结论：

- 本周未发现新的不可迁移 case-specific shortcut

---

## 7. Week 1 最终结论

本次判定为：

- `通过`

判定理由：

- 第二真实 case 已接入
- 第二 case 已通过同一 governed 主链完成真实执行
- clarify -> resume -> second case success 已成立
- 本周成功定义已经被真实运行证据满足

但需要明确：

- 这不意味着总体设计已落地
- 这也不意味着多 skill 或 catalog-first 已成立
- 它只意味着 Phase3-B / Week 1 的目标已经从“单 case 闭环”推进到了“最小 case space 成立”

---

## 8. 下一步建议

Week 1 通过后，下一步应进入 Week 2：

- 先完整资产化 `water_yield`
- 至少让一个主链节点在缺失 skill asset 时阻断推进
- 不要在 Week 2 提前扩成第二 skill 的伪完成

如果需要对外给一句最稳的表述，建议使用：

> 截至 2026-04-02，系统已完成从单一真实 annual water yield case 到最小双 case governed projection 的过渡，并已通过 direct-hit、second-case success 和 ambiguity/clarify/resume 三类真实运行验证；但当前仍处于单真实 skill、未 catalog-first 的早期工程化阶段。
