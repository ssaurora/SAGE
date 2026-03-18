下面给出一版 **Sprint 1 / Sprint 2 / Sprint 3 排期表**。  
目标很明确：3 个 Sprint 只服务两件事——先把 **Phase 0 跑通**，再把 **Phase 1 站稳**。整个排期仍然遵守前面已经确定的硬约束：规划面从第一天起保留 Pass 1 / Pass 2 结构，控制面持有唯一主状态机与放行权，执行面尽早进入独立运行单元，数据面以 `TaskState` 和 append-only 事件/审计为核心，不允许 Redis 成为事实源。

---

# Sprint 1：把骨架立起来

## Sprint 目标

建立最小治理骨架、最小前后端交互、最小运行镜像，为 Sprint 2 的端到端打通做准备。  
这一轮**不追求结果漂亮**，只追求“结构对”。

## 核心交付

### 1. 控制面骨架

- 建 `task_state`
    
- 建 `event_log`
    
- 建 `audit_record`
    
- 实现最小状态机
    
- 实现 `POST /tasks`
    
- 实现 `GET /tasks/{taskId}`
    
- 实现 `GET /tasks/{taskId}/events`
    

Done 标准：

- 任务可创建
    
- 状态可推进
    
- 事件可查询
    
- 并发更新不覆盖写
    

依据：控制面第一步先落 `TaskState`、主状态机、`state_version` 与基础事件流；数据面也要求这几张表作为最先落地对象。

### 2. 规划面 Pass 1

- 固定 `water_yield` 单模板
    
- 输出 `selected_template`
    
- 输出 `logical_input_roles`
    
- 输出 `slot_schema_view`
    
- 输出 `graph_skeleton`
    

Done 标准：

- 控制面能先拿到角色清单和槽位视图
    
- 后续绑定逻辑全部建立在 Pass 1 输出上
    

依据：规划面要求 Pass 1 先声明角色、槽位和逻辑图骨架。

### 3. 运行镜像

- 产出固定 Docker 镜像
    
- 内置 InVEST + GIS 依赖
    
- 固定输入/输出/日志目录约定
    

Done 标准：

- 开发联调和正式演示都能在同一镜像下启动
    
- 不再依赖开发者本机裸环境
    

依据：执行面要求 job 对应独立运行单元，基础隔离需前移。

### 4. 前端任务骨架

- 自然语言输入页
    
- 任务详情页骨架
    
- 状态与事件展示
    

Done 标准：

- 用户能创建任务并看到状态变化
    

---

## 推荐 Owner

前端：

- 输入页
    
- 任务详情页骨架
    
- 事件列表
    

Spring Boot：

- `task_state / event_log / audit_record`
    
- 最小状态机
    
- `/tasks`、`/tasks/{id}`、`/events`
    
- 调 Planning Pass 1
    

Python / DevOps：

- Docker 镜像
    
- 本地容器启动脚本
    
- 目录约定
    

数据库：

- 三张核心表建表
    
- 索引与乐观锁字段
    

---

## Sprint 1 不做什么

- 不做 Pass 2
    
- 不做真实 analysis job
    
- 不做结果页
    
- 不做 `WAITING_USER`
    
- 不做 `/resume`
    
- 不做 workspace 生命周期
    

---

## Sprint 1 验收会要回答的问题

- 是否已经摆脱“脚本直拼参数”的隐式流程
    
- 是否已建立控制面统一任务对象
    
- 是否已具备统一演示环境
    

---

# Sprint 2：把 Phase 0 跑通

## Sprint 目标

打通从自然语言到一次真实 `water_yield` 执行结果的闭环。  
这一轮结束后，系统必须能“真正跑一次”。

## 核心交付

### 1. Cognition Pass B

- 读取 `logical_input_roles`
    
- 生成 `slot_bindings`
    
- 生成 `args_draft`
    
- 输出 `decision_summary`
    
- 输出 `manifest_payload_candidate`
    

Done 标准：

- 认知面输出的是绑定候选和参数草案，而不是最终执行参数
    
- 绑定与参数草案可独立校验
    

依据：认知面和总体设计都要求认知面围绕角色与槽位进行结构化补全，不得越权做执行图决定。

### 2. Planning Pass 2

- 输入 `slot_bindings + args_draft + graph_skeleton`
    
- 输出 `materialized_execution_graph`
    
- 输出最小 `runtime_assertions`
    
- 输出 `planning_summary`
    

Done 标准：

- Pass 2 的存在是真实的，不是把 Pass 1 和执行脚本揉成一层
    
- 控制面拿到的是最小执行对象，而不是立即开跑
    

依据：规划面要求 `graph_skeleton` 与 `materialized_execution_graph` 分离保存。

### 3. Validation Gate

- 校验 `slot_bindings`
    
- 校验 `args_draft`
    
- 校验执行对象
    
- 不通过则进 `WAITING_USER` 或 `FAILED`
    

Done 标准：

- 放行权只在控制面
    
- 认知面/规划面不自行进入执行
    

依据：控制面统一持有 validate 调度与执行放行权。

### 4. Job Runtime

- 建 `job_record`
    
- analysis job 提交
    
- `ACCEPTED / QUEUED / RUNNING / SUCCEEDED / FAILED / CANCELLED`
    
- heartbeat 回传
    

Done 标准：

- 一次任务能稳定提交并完成一次真实 analysis job
    

依据：执行面第一阶段先落 job runtime，数据面也要求补执行相关对象。

### 5. 最小 `result_bundle`

- 主要输出
    
- 日志
    
- 关键指标摘要
    
- 失败信息
    

Done 标准：

- 前端结果页展示结构化结果，不展示杂乱目录路径
    

依据：执行面与总体设计都要求结果先结构化再解释。

### 6. 最小 cancel

- `/cancel`
    
- 停掉主进程或容器
    
- 更新 `job_record` 与 `task_state`
    

Done 标准：

- cancel 不是假取消
    

依据：执行面强调 cancel 必须有物理终止路径。

### 7. 前端结果页

- 状态流展示
    
- 结果摘要展示
    
- 最终解释展示
    

Done 标准：

- 一条任务从创建到完成，前端全链可见
    

---

## 推荐 Owner

前端：

- 结果页
    
- 状态持续刷新
    
- 错误摘要区
    

Spring Boot：

- Cognition Pass B 编排
    
- Planning Pass 2 调度
    
- Validation Gate
    
- `/result`
    
- `/cancel`
    

Python：

- analysis worker
    
- `job_record`
    
- `result_bundle`
    
- cancel 真终止
    

数据库：

- `job_record`
    
- 最小 `analysis_manifest`
    
- 结果相关字段
    

---

## Sprint 2 不做什么

- 不做 Repair Dispatcher
    
- 不做 `/resume`
    
- 不做结构化补数面板
    
- 不做 workspace archive/demolish
    
- 不做 registry 治理
    

---

## Sprint 2 验收会要回答的问题

- 是否已经真正跑通 `water_yield`
    
- Pass 1 / Pass 2 是否真实存在
    
- 结果是否已结构化
    
- 演示路径是否完全运行在固定镜像内
    

---

# Sprint 3：把 Phase 1 站稳

## Sprint 目标

建立 `WAITING_USER`、修复分流、workspace 生命周期和结果工件索引，使系统具备“可反复试用”的基本质量。  
这一轮结束后，系统不只是会跑，还会**告诉用户为什么没跑成，以及下一步该怎么补**。

## 核心交付

### 1. `WAITING_USER` 结构化对象

- `missing_slots`
    
- `invalid_bindings`
    
- `required_user_actions`
    
- `resume_hint`
    

Done 标准：

- 前端能明确告诉用户缺什么、错什么、补什么
    

依据：认知面与控制面均将结构化等待态和修复建议列为关键对象。

### 2. 补数面板 + `/resume`

- 文件补传
    
- 触发 `/resume`
    
- 恢复前检查
    
- 回到后续阶段
    

Done 标准：

- 用户补数后不需要重新建任务
    

依据：控制面已将 `/resume` 和等待态治理作为后续关键步骤。

### 3. Repair Dispatcher

- 参数/绑定错误 -> 认知面
    
- 图结构/预处理错误 -> 规划面
    
- 缺数/元数据未就绪 -> `WAITING_USER` / `WAITING_SYSTEM`
    
- 致命错误 -> `FAILED`
    

Done 标准：

- 常见失败不再全部落成一个通用失败页面
    

依据：控制面明确把 Repair Dispatcher 作为修复分流中枢。

### 4. workspace 生命周期

- `workspace_registry`
    
- create
    
- cleanup
    
- archive
    
- demolish
    
- 任务/job/run revision 关联
    

Done 标准：

- 任一 workspace 都可追溯、可清理
    
- 临时目录不再无主漂浮
    

依据：执行面要求显式 workspace 生命周期；数据面明确 `workspace_registry` 属于第二阶段关键表。

### 5. `result_bundle_record + artifact_index`

- 结构化记录 bundle
    
- 记录主要工件索引
    
- 区分 `primary_outputs / intermediate_outputs / logs / audit_artifacts`
    

Done 标准：

- 工件可追溯到 workspace 与 run revision
    
- 不把大文件塞数据库，不把 URI 直接塞进 `TaskState`
    

依据：数据面已明确 `result_bundle_record` 和 `artifact_index` 的职责边界。

### 6. Redis 运行态协调

- heartbeat
    
- lease
    
- cancel token
    

Done 标准：

- Redis 只承担高频协调，不承担主事实存储
    

依据：数据面明确 Redis 不能成为唯一事实源。

### 7. Capability / Provider Registry 最小版

- `capability_registry`
    
- `provider_registry`
    
- 注册 validate / submit job / query job / cancel / workspace / result
    

Done 标准：

- 不再硬编码 provider 地址
    
- 长时能力调用开始走异步 contract
    

依据：能力面要求 capability contract 成为唯一合法出口，并采用 registry + provider-first 形态。

---

## 推荐 Owner

前端：

- `WAITING_USER` 补数面板
    
- `/resume` 交互
    
- 工件页
    

Spring Boot：

- `WAITING_USER` 对象
    
- Repair Dispatcher
    
- `/resume`
    
- registry 最小版
    

Python：

- workspace 生命周期
    
- result bundle 完整化
    
- cancel 真终止增强
    

数据库：

- `workspace_registry`
    
- `result_bundle_record`
    
- `artifact_index`
    
- registry 表
    

---

## Sprint 3 验收会要回答的问题

- 系统能否区分“缺数”和“格式不兼容”
    
- 用户能否在等待态下获得明确补数指引
    
- 用户补数后是否可以恢复
    
- 运行临时目录是否已经纳入治理
    
- 工件是否可追溯
    

---

# 建议节奏

## Sprint 周期

我建议每个 Sprint 按 **2 周** 走。这样总共 6 周左右，你就能得到：

- Sprint 1：结构骨架
    
- Sprint 2：Phase 0 闭环
    
- Sprint 3：Phase 1 稳定化
    

## 每个 Sprint 的固定节奏

第 1 天：

- Sprint Planning
    
- 锁定 Sprint Goal
    
- 锁定演示链路
    

第 5 天：

- 中期检查
    
- 只看闭环，不看“模块完成率”
    

最后 1 天：

- Sprint Review
    
- 用真实任务演示
    
- 以“是否打通链路”为首要验收标准
    

---

# 最后的建议

这 3 个 Sprint 里，你最该盯死的不是“完成了多少 Story”，而是三条线：

第一条，**Planning Pass 1 / Pass 2 是否一直被保留**。  
第二条，**正式执行是否一直运行在固定隔离环境中**。  
第三条，**Sprint 3 结束时 `WAITING_USER` 是否已经结构化**。

这三条如果守住，后面的 Phase 2 才有资格谈恢复事务、冻结、状态损坏治理和工业级扩展。

我也可以把这份 Sprint 计划继续整理成 **表格版项目计划书**，更适合直接贴进你的设计文档。