# Phase2 正式开发任务树 V2

## 状态更新（2026-03-27）

当前项目状态已从“Phase2 起步前”推进为：

- `Phase2-A` 已关闭
- `Phase2-B` 已关闭
- `Phase2` 当前规划范围内的主线已完成
- 当前治理主线已按 `Phase2-B` 冻结基线收口

因此，本文件当前主要作为 Phase2 的历史任务树与边界说明使用，而不再作为进行中的执行清单。

---

## 1. 文档定位

本文件用于定义 **Phase2 的正式开发任务树 V2**。

这版文档保留了此前任务树中正确的部分：

- Skill / Capability 抽象
- validation / waiting_context / repair 对齐
- 前端 authority / advisory 边界意识

同时补齐了此前覆盖不足、但在正式路线图中属于 Phase2 核心主线的内容：

- 控制面强治理恢复链
- Manifest 冻结与版本解耦
- 规划面工业编译器增强
- 断言下推与失败一等公民
- checkpoint / recovery contract
- `STATE_CORRUPTED` 等损坏治理

因此，这份文档不再把 Phase2 定义为单纯的“能力抽象收口阶段”，而是定义为：

> **Phase2-A：骨架收口**  
> **Phase2-B：治理主线成型**

---

## 2. 当前判断与 Phase2 目标

根据 `当前进度与架构差距评估`，当前项目处于：

- **Phase 1 基线基本成立**
- **Phase 1 后期，Phase 2 起步前**

当前系统已经具备：

- `WAITING_USER -> upload -> resume -> execution`
- `result_bundle / artifacts / runs / workspace`
- repair proposal 与最小 LLM 调试能力
- task / planning / cognition / repair / execution 的基本分层骨架

但当前距离正式架构设计仍存在三类关键差距：

- Skill / Capability 抽象尚未成为系统骨架
- 恢复链还不是强治理事务
- Manifest / planner / assertion / recovery contract 仍未形成正式治理主线

因此，Phase2 的正式目标不能只写成“把能力抽象补上”，而必须同时完成：

- 能力抽象
- 事实派生
- 恢复治理
- 编译治理
- 执行失败治理

---

## 3. Phase2 总目标

Phase2 的总目标是把系统从：

> “已有闭环、但治理较弱的 Phase1 基线”

推进为：

> “具备能力模型、恢复事务、Manifest 冻结、规划编译增强和失败治理骨架的强治理版系统”

---

## 4. Phase2 完成标准

Phase2 结束时，至少应满足以下标准：

- skill / capability 成为系统真实驱动对象，而不是隐式知识
- `waiting_context`、`required_user_actions`、`repair_proposal` 能追溯到 capability + validation 事实
- `/resume` 具备最小事务语义，而不只是重新点一次继续运行
- Manifest 在执行前具备正式冻结锚点
- planning 输出具备最小工业编译器特征，而不只是弱化图生成
- runtime assertion 能进入执行链，并把断言失败建模为结构化失败
- recovery / checkpoint / version 边界明确
- 前端能严格区分 authority facts、LLM suggestion、execution result

---

## 5. Phase2 总结构：A 段 + B 段

## 5.1 Phase2-A：骨架收口

这是 Phase2 的启动段，目标是把当前最迫切的骨架问题补齐，为治理主线铺地基。

包含三个工作包：

- `P2-1` Skill / Capability 基础模型
- `P2-2` Validation / Waiting / Repair 对齐
- `P2-3` Repair Proposal 规范化

## 5.2 Phase2-B：治理主线

这是 Phase2 的正式主体，目标是把恢复、冻结、编译和失败治理拉到正式架构要求上。

包含四个工作包：

- `P2-4` 控制面强治理恢复链
- `P2-5` Manifest 冻结与版本解耦
- `P2-6` 规划面工业编译器增强
- `P2-7` 断言下推、失败建模、前端边界与验收收口

建议总顺序：

`P2-1 -> P2-2 -> P2-3 -> P2-4 -> P2-5 -> P2-6 -> P2-7`

---

## 6. P2-1：Skill / Capability 基础模型

### 目标

定义最小 Skill Schema 与 Capability Schema，使系统开始通过“能力声明”而不是“散落规则”驱动行为。

### 任务

- 定义最小 `SkillDefinition`
- 定义最小 `CapabilityDefinition`
- 定义 skill 所需 `required_roles / required_slots`
- 定义 `output_contract`
- 定义 `validation_hints`
- 定义 `repair_hints`
- 定义 `execution_profile`
- 明确 skill 与 template / runtime / capability 的映射关系

### 建议字段

最小 `SkillDefinition` 建议至少包含：

- `skill_id`
- `display_name`
- `required_roles`
- `optional_roles`
- `output_contract`
- `validation_hints`
- `repair_hints`
- `execution_profile`

### 产出物

- 技术设计文档
- schema 文件或定义类
- 至少一个示例 skill（例如 `water_yield`）

### 完成标准

- skill 要求不再只存在于 prompt 或分支逻辑里
- validation / repair 可以读取统一 skill 定义
- capability 不再只是概念表，而成为后续派生链的输入

---

## 7. P2-2：Validation / Waiting / Repair 对齐

### 目标

让 `waiting_context`、`required_user_actions` 与 repair 输入上下文，从“当前控制层派生对象”推进为“基于 capability/validation 事实的标准派生对象”。

### 任务

- 盘点当前 `waiting_context` 来源字段
- 建立 `validation_summary -> waiting_context` 的标准映射
- 将 `required_user_actions` 与 capability 要求对齐
- 将 `missing_slots / invalid_bindings` 与 skill schema 对齐
- 将 repair proposal 输入上下文与 validation/capability 事实对齐
- 明确 `can_resume` 的重计算触发点
- 确保 attachment upload / override 后重新评估 waiting context

### 重点约束

- Dispatcher 仍然是 authority
- LLM 不决定 `can_resume`
- 前端不推断 repair 语义

### 产出物

- waiting_context 派生规则说明
- repair 输入上下文说明
- 后端映射代码收敛
- 最小测试用例

### 完成标准

- 当前 WAITING_USER 的结构化字段可以追溯到 capability + validation 事实
- waiting_context 不再依赖隐式 UI 或 prompt 假设
- repair proposal 输入不再脱离 skill/capability 事实

---

## 8. P2-3：Repair Proposal 规范化

### 目标

将 repair proposal 从“临时 LLM 辅助输出”推进到“受明确 schema 和能力边界约束的认知输出”。

### 任务

- 明确 repair proposal 输入 schema
- 明确 repair proposal 输出 schema
- 收紧 prompt 的输出边界
- 统一 fallback 策略
- 保留可控 debug 能力
- 明确哪些 debug 信息只用于开发态
- 明确什么时候必须退回 rule-based fallback

### 关键问题

- 什么时候允许 LLM 生成用户可见解释
- 什么时候必须使用结构化 fallback
- 如何避免模型输出脱离 capability / validation 事实

### 产出物

- repair proposal schema 说明
- repair provider/fallback 策略说明
- 调试开关与开发态输出规范

### 完成标准

- repair proposal 可预测、可调试、可回退
- LLM 输出不承担 authority，只承担 explanation / suggestion
- repair proposal 已能稳定嵌入 WAITING_USER / resume 主链路

---

## 9. P2-4：控制面强治理恢复链

### 目标

把当前“可工作的 `/resume`”升级为“最小可治理恢复事务”。

### 任务

- 正式引入 `MIN_READY` 概念
- 定义 `delta inventory` 合并边界
- 明确恢复前的最小元数据就绪检查
- 引入 Ack / Commit / Rollback 恢复节奏
- 引入 `checkpoint_version` / `inventory_version` / `state_version` 对齐关系
- 明确 `resume_request_id` 的治理语义
- 设计恢复失败后的受限态策略
- 明确何时进入 `STATE_CORRUPTED`
- 明确 `FORCE_REVERT_CHECKPOINT` 的最小治理路径

### 重点约束

- `/resume` 不能只是“重新提交”
- 控制面是唯一事实源
- 未被控制面承认的未来态不得推进主链路

### 产出物

- 恢复事务状态流图
- 恢复输入/输出/承认对象说明
- 控制面实现计划
- 最小恢复事务测试集

### 完成标准

- `/resume` 已具备最小事务语义
- 恢复失败时可以 Rollback，而不是进入伪恢复态
- `STATE_CORRUPTED` 具备明确进入条件和受限行为

---

## 10. P2-5：Manifest 冻结与版本解耦

### 目标

建立执行前正式冻结的 `AnalysisManifest`，为恢复、回放、审计和版本治理提供锚点。

### 任务

- 明确 `manifest_payload_candidate` 与 `planning_manifest_payload` 的合并规则
- 定义 manifest freeze gate
- 定义冻结前允许变动的对象边界
- 定义冻结后不可变对象边界
- 明确 `manifest_id / manifest_version / planning_revision / checkpoint_version / run_revision` 的关系
- 明确 manifest 与 projection/read model 的关系
- 明确 manifest 与 artifact / result / run 的指针关系

### 重点约束

- Manifest 必须是被控制面正式承认的对象
- 冻结后不能由下游隐式改写
- projection 不能反向成为 manifest 事实源

### 产出物

- manifest freeze 规则文档
- 版本解耦说明
- manifest 相关数据对象与 API 调整方案

### 完成标准

- Manifest 具备正式冻结锚点
- 运行轮次、规划轮次、认知 checkpoint 不再混为一体
- 恢复、回放、审计已有正式可引用对象

---

## 11. P2-6：规划面工业编译器增强

### 目标

把当前的弱化规划面，推进为具备最小工业编译器特征的规划面。

### 任务

- 增强 `Static Graph Validator`
- 引入最小 `Graph Digest Builder`
- 引入最小 `Planning Summary Builder`
- 建立 `rewrite_registry`
- 引入最小 `Canonicalizer` 白名单
- 引入最小 `Runtime Assertion Builder`
- 明确 Pass 1 / Pass 2 输出边界与冻结输入边界

### 为什么必须独立成包

Skill/Capability 抽象本身不足以完成 Phase2。  
若没有更强的 planning compiler，Manifest 冻结、恢复事务和断言下推都不会稳。

### 产出物

- planning compiler 最小增强设计文档
- digest / summary / rewrite 最小对象定义
- Pass 1 / Pass 2 输出对齐说明

### 完成标准

- planning 输出不再只是“能跑的草案”
- 已具备最小静态校验、摘要、断言生成能力
- 规划面开始具备“工业级确定性编译器”的雏形

---

## 12. P2-7：断言下推、失败建模、前端边界与验收收口

### 目标

把断言失败正式纳入执行治理，并在前端与验收层完成边界收口。

### 任务

- 将 runtime assertion 正式推入执行链
- 设计断言失败的结构化失败模型
- 明确 preprocess / analysis 的失败边界
- 明确断言失败、执行失败、repair 等待态的区分
- 区分 authority panel 与 suggestion panel
- 将 waiting_context 作为 authority facts 展示
- 将 repair_proposal 作为 advisory 展示
- 优化 result / runs / artifacts / workspace 的关联展示
- 制定 Phase2 最终验收清单

### 重点约束

- 断言失败不能伪装成成功
- 前端不重新解释业务规则
- UI 收口必须建立在治理主线完成之后

### 建议展示分区

- `System Status`
- `Required User Actions`
- `Repair Proposal`
- `Execution History`
- `Artifacts / Workspace`

### 产出物

- 断言失败建模说明
- 前端展示边界设计
- Phase2 验收清单

### 完成标准

- 断言失败成为一等公民
- 用户能清楚区分系统决定与 LLM 建议
- 前后端边界符合 AGENTS 与架构设计要求

---

## 13. 推荐里程碑

### Milestone A：能力骨架成型

- 完成 `P2-1`
- 完成 `P2-2`
- 完成 `P2-3`

目标：

- skill / capability 正式进入主链路
- waiting / repair 已与 capability 事实对齐

### Milestone B：恢复治理与冻结成型

- 完成 `P2-4`
- 完成 `P2-5`

目标：

- `/resume` 成为最小事务
- Manifest 成为正式冻结锚点

### Milestone C：编译与失败治理成型

- 完成 `P2-6`
- 完成 `P2-7`

目标：

- planning compiler 具备最小工业特征
- assertion / failure / UI / acceptance 一起收口

---

## 14. 当前建议的第一批具体开发任务

如果现在立刻开始做，建议按下面顺序开工：

- 任务 1：设计最小 `SkillDefinition`
- 任务 2：为 `water_yield` 写第一份 skill 定义
- 任务 3：梳理 validation 输出与 skill/capability 的映射
- 任务 4：梳理 waiting_context 与 repair 输入上下文的派生规则
- 任务 5：规范 repair proposal schema 与 fallback 策略
- 任务 6：设计恢复事务最小状态图（`MIN_READY` / Ack / Commit / Rollback）
- 任务 7：设计 manifest freeze gate 与版本解耦草案

这意味着：

当前立即开工的重点仍然是 **Phase2-A**，  
但文档层面已经把 **Phase2-B** 作为正式主线提前确定，避免团队误以为“把 skill/capability 做出来就算 Phase2 完成”。

---

## 15. 不要做的事

Phase2 期间，仍然不建议引入以下内容：

- 多 skill 编排复杂化
- 多 provider 路由扩张
- 大规模 prompt 重写
- 全面 rerun / retry 框架
- 并行工作流引擎
- 把业务修复逻辑推回前端
- 把 prompt/provider 逻辑继续塞进 Java backend
- 用 UI 收口替代治理主线建设

---

## 16. 一句话结论

Phase2 不能只做“能力抽象版 Phase1”。

Phase2 的正确任务形态应当是：

> **A 段先补骨架：Skill / Capability / Waiting / Repair**  
> **B 段再立治理：Resume 事务 / Manifest Freeze / Planning Compiler / Assertion Failure**

只有这样，Phase2 才能既顺着当前代码现状演进，又真正对齐正式路线图中的“强治理版”定义。
