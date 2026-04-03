# Phase3-B / Week 4 开发记录
更新日期：2026-04-03

## 0. 文档目的

本文用于记录 Phase3-B Week 4 的实际开发结果，并对照四周推进计划中 Week 4 的目标，说明：
- 本周真正完成了什么
- 哪些控制层领域硬编码已经被收紧
- 最小能力面 contract 在哪里开始被真实消费
- 哪些边界仍未完成，不应被夸大

Week 4 的目标不是重写平台，也不是把系统说成“完整 MCP / 完整能力面已落地”，而是：

> 让控制层更接近治理主机，清理一批不该长期保留的领域硬编码，同时让最小 contract 从 asset 文件进入真实运行输出与读模型。

---

## 1. 本周目标与成功定义

Week 4 的成功不以“写出一组 contract 文件”为准，而以以下三点是否成立为准：

1. control layer 不再承担一部分明显不该长期保留的 case 语义与参数默认值语义
2. 最小 contract 集已经进入真实运行路径，而不是停留在 asset 文件中
3. Week 1 到 Week 3 的双 case governed 主链、skill 资产化主链、catalog slice 主链没有被破坏

因此，Week 4 关注的是“边界变薄”和“contract 开始掌权”，不是平台扩张。

---

## 2. 本周实际完成内容

## 2.1 Control Layer：去除 case 名称驱动的确定性路由信号

本周对 [GoalRouteService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/GoalRouteService.java) 做了收口。

当前变化是：
- 不再把 `gura / blue nile / upper nile` 这类 case 名称当作 control-layer 的确定性 skill routing 信号
- `inferPrimarySkill(...)` 只保留更通用的 `water_yield / water yield / precipitation / eto / yield` 级别信号
- real-case 偏好判断只保留更薄的通用意图信号，如 `real case / invest / 真实`

这一步的意义是：
- control layer 不再显式拥有 case 语义知识
- case 选择进一步收回到 cognition / registry / governed projection 路径

对应测试见：
- [GoalRouteServiceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/GoalRouteServiceTest.java)

---

## 2.2 Control Layer：去除 Java 侧语义默认值兜底

本周继续清理了不应长期放在 Java 控制层的默认参数兜底逻辑。

涉及文件：
- [ExecutionContractAssembler.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/ExecutionContractAssembler.java)
- [SemanticDefaultResolver.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/SemanticDefaultResolver.java)
- [Pass1FactHelper.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/planning/Pass1FactHelper.java)
- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

当前变化是：
- `seasonality_constant` 不再由 Java 侧硬编码兜底
- `root_depth_factor` 不再由 Java 侧硬编码 `0.8`
- `pawc_factor` 不再由 Java 侧硬编码 `0.85`
- 这些值现在只在 `pass1` 已冻结 `stable_defaults` 时被消费

这一步的意义是：
- control layer 不再偷偷重新定义 capability 语义
- skill / pass1 冻结下来的稳定事实开始真正掌权

对应测试见：
- [ExecutionContractAssemblerTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/ExecutionContractAssemblerTest.java)

---

## 2.3 能力面最小 contract：从 asset 文件进入 skill 定义

本周没有新建厚重平台，而是沿 Week 2 的 skill asset 路径，把最小 contract 集正式纳入 `water_yield` skill 资产。

对应文件：
- [mcp_tools_map.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/mcp_tools_map.yaml)
- [skill_assets.py](/e:/paper_project/SAGE/Service/planning-pass1/app/skill_assets.py)
- [schemas.py](/e:/paper_project/SAGE/Service/planning-pass1/app/schemas.py)

当前 contract 集包括：
- `inspect_asset_facts`
- `validate_bindings`
- `validate_args`
- `checkpoint_resume_ack`
- `submit_job`
- `query_job_status`
- `collect_result_bundle`

每个 contract 至少带有：
- `input_schema`
- `output_schema`
- `side_effect_level`
- `caller_scope`
- `idempotency`
- `cancel_semantics`
- `audit_requirement`

这一步的意义是：
- contract 不再只是文档概念
- capability facts 开始能够携带正式的 contract 元信息
- Week 4 仍保持 thin contract 路线，没有引入新的厚层

---

## 2.4 Service：contract 已进入真实 pass1 输出

本周把 skill asset 中的 contract 元信息接入到了 pass1 输出的 `capability_facts` 中。

对应文件：
- [schemas.py](/e:/paper_project/SAGE/Service/planning-pass1/app/schemas.py)
- [skill_assets.py](/e:/paper_project/SAGE/Service/planning-pass1/app/skill_assets.py)
- [test_pass1_api.py](/e:/paper_project/SAGE/Service/planning-pass1/tests/test_pass1_api.py)

当前 `CapabilityDefinitionLite` 已带出：
- `contracts`

而 `test_pass1_api.py` 已验证：
- `capability_facts.contracts` 中存在 Week 4 定义的最小 contract 集
- 其中至少部分字段会进入真实 API 输出，例如：
  - `validate_args.input_schema`
  - `submit_job.side_effect_level`

这说明：
- contract 已经进入 service 真实输出
- 不再只是 asset 文件被动存在

---

## 2.5 Backend 读模型：contract 已进入 manifest / detail 投影

本周继续把 contract 从 service 输出推进到了 backend 读模型。

涉及文件：
- [Pass1Response.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/planning/dto/Pass1Response.java)
- [TaskManifestResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskManifestResponse.java)
- [TaskDetailResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskDetailResponse.java)
- [TaskProjectionBuilder.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskProjectionBuilder.java)
- [TaskProjectionBuilderTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskProjectionBuilderTest.java)

当前变化是：
- `Pass1Response.CapabilityFacts` 已可接收 `contracts`
- `TaskManifestResponse.capability_facts` 已带出完整 `contracts` map
- `TaskDetailResponse.pass1_summary` 已带出：
  - `contract_count`
  - `contract_names`

这一步的意义是：
- contract 已进入 backend 的正式投影，而不是只停留在 service 测试里
- Week 4 的最小能力面 contract 现在已经能被治理/调试读模型消费

---

## 2.6 Control Layer：进一步压薄 provider/runtime preference 注入

本周还进一步收紧了 deterministic Java fallback 对 provider/runtime 的注入。

对应文件：
- [GoalRouteService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/GoalRouteService.java)

当前变化是：
- deterministic fallback 只保留 `execution_mode`
- 不再在 Java fallback 中显式写入：
  - `provider_preference`
  - `runtime_profile_preference`

这一步的意义是：
- control layer 不再主动制造 provider/runtime 偏好暗示
- 真正的 provider 解析权继续保留在 capability / registry / contract 路径中

---

## 3. 本周测试与验证

本周已完成以下验证。

## 3.1 Service 测试

执行：

```powershell
pytest Service/planning-pass1/tests/test_pass1_api.py -q
```

结果：
- `25 passed`

本周新增或覆盖的验证点包括：
- pass1 输出带出 `capability_facts.contracts`
- contract 元信息字段可被稳定读取

---

## 3.2 Backend 测试

执行：

```powershell
mvn -q "-Dtest=GoalRouteServiceTest,ExecutionContractAssemblerTest,TaskServiceGovernanceTest,TaskServiceCognitionFlowTest,TaskProjectionBuilderTest" test
```

结果：
- 通过

本周新增或覆盖的验证点包括：
- control layer 不再把 case 名称当作确定性 skill signal
- assembler 不再用 Java 侧硬编码默认值补齐参数
- manifest/detail 投影已带出 contract 相关字段

---

## 3.3 真实链路回归

本周至少回归了以下真实链路。

### Success

- 场景：`Success`
- 结果：通过
- task id：`task_20260403074448715_eccb5855`

### Clarify

- 场景：`Clarify`
- 结果：通过
- task id：`task_20260403072837290_e28ead7b`

这说明：
- Week 4 当前改动没有破坏 Week 1 的双 case governed 主链
- clarify / resume / real execution 仍可正常通过

---

## 4. 本周替换了哪些旧逻辑

Week 4 不是单纯新增 asset 文件，本周已经存在明确的“旧逻辑被收紧或可删除”的证据。

### 已收紧

- `GoalRouteService` 中 case 名称驱动的确定性路由信号
- Java 侧 `seasonality_constant / root_depth_factor / pawc_factor` 硬编码兜底
- deterministic Java fallback 中的 `provider_preference / runtime_profile_preference` 注入

### 已进入正式投影

- `mcp_tools_map.yaml` 中的最小 contract 集
- service pass1 输出中的 `capability_facts.contracts`
- backend manifest/detail 投影中的 contract 元信息

因此，Week 4 已经满足“不是只写文件，而是开始替换旧债务和让 contract 掌权”的最低要求。

---

## 5. 本周没有完成的内容

为避免夸大阶段完成度，这里明确记录 Week 4 尚未完成的边界。

### 5.1 还没有完整能力面平台

当前没有：
- 独立 capability gateway
- 独立 contract registry service
- 完整 caller scope / auth enforcement 体系
- contract 级别的统一审计平台

因此，Week 4 的正确表述应是：

> 最小 contract 已进入 skill asset、service 输出和 backend 读模型，但完整能力面平台尚未建立。

### 5.2 provider/runtime 解析权尚未完全抽离

虽然 control-layer 的 deterministic fallback 已被压薄，但：
- `RegistryService` 仍负责 provider resolution 的实际治理路径
- capability -> provider 的更完整协商体系仍未建设

这仍属于可接受的过渡态，而不是最终形态。

### 5.3 前端尚未做 contract 的正式产品化展示

当前 contract 元信息主要进入：
- service 输出
- backend 读模型
- 治理/调试视图可用的后端契约

但普通用户侧前端仍未将其做成正式产品心智。这与总体设计中的“用户侧不暴露 Skill / internal complexity hidden”是一致的，因此不构成 Week 4 缺陷。

---

## 6. 阶段结论

本周结论为：

- `Go`

原因是：
- control layer 已进一步退出一部分不该长期保留的领域知识承担者角色
- 最小 contract 集已从 asset 文件进入真实 service 输出与 backend 读模型
- Week 1 到 Week 3 的真实链路回归保持通过

但必须明确：

> Week 4 完成的是“控制层进一步变薄 + 最小 contract 开始被真实消费”，不是“完整能力面平台已经落地”。

---

## 7. 下一步建议

Week 4 完成后，下一阶段最合理的方向不是继续扩 UI，而是做 Phase3-B 的阶段收束与向下一阶段切换。

优先建议：

1. 整理 Phase3-B 总结文档  
   把 Week 1 到 Week 4 的结果串成统一阶段结论。

2. 明确进入下一阶段的主轴  
   当前最自然的下一步是：
   - 扩展更正式的 catalog-first 路径
   - 或进入更系统的 capability / contract 消费收口

3. 不要在当前节点重新膨胀 control layer  
   Week 4 已经开始收缩控制层领域耦合，后续不应再通过 Java 快捷逻辑换取短期成功。

