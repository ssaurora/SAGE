# Phase2-B 落地清单

更新日期：2026-03-27

## 1. 当前结论

当前工作树上的 `Phase2-B` 已经不再是“刚开工”，而是已经进入“主链打通、治理能力基本落地、剩余问题主要集中在补测与收尾”的阶段。

按本轮代码与验收结果判断：

- `B1 控制面强治理恢复链`：已基本落地
- `B2 Manifest 冻结与版本解耦`：已落地
- `B3 规划面最小工业编译器增强`：已落地
- `B4 Assertion / Promotion / 前端收口`：已基本落地

当前更准确的描述不是“Phase2-B 未完成”，而是：

> `Phase2-B` 主线能力已经进入可用状态，剩余缺口主要是补齐少量针对性自动化验证、兼容性验收和最终收口文档。

---

## 2. 已完成项

### 2.1 B1：控制面强治理恢复链

- `task_state` 已引入治理字段：
  - `planning_revision`
  - `checkpoint_version`
  - `inventory_version`
  - `resume_txn_json`
  - `corruption_reason`
  - `corrupted_since`
- 新状态已进入主链：
  - `RESUMING`
  - `ARTIFACT_PROMOTING`
  - `STATE_CORRUPTED`
- `/resume` 已具备事务语义：
  - 进入 `RESUMING` 时先写 `PREPARING`
  - 只有 candidate manifest / candidate job 持久化完成后才写 `ACKED`
  - commit 后写 `COMMITTED`
  - recoverable validation 回滚到 `WAITING_USER`
  - 无法对齐时进入 `STATE_CORRUPTED`
- `MIN_READY` 已升级为更严格判断：
  - required input 需要由“最小元数据完整的已落库 attachment”或“accepted override”满足
  - 不再仅凭 slot 名存在就判定可恢复
- `STATE_CORRUPTED` 下：
  - upload 被拒绝
  - resume 被拒绝
  - 管理员可执行 `force-revert-checkpoint`

### 2.2 B2：Manifest 冻结与版本解耦

- `analysis_manifest` 已扩展治理字段：
  - `freeze_status`
  - `planning_revision`
  - `checkpoint_version`
  - `graph_digest`
  - `planning_summary_json`
  - `capability_key`
  - `selected_template`
  - `template_version`
- manifest 生命周期已拆成：
  - `buildManifestCandidate(...)`
  - `freezeManifestOnCommit(...)`
- authority manifest 指针只在 commit 时更新
- 历史 manifest 已通过迁移回填 `freeze_status='FROZEN'`

### 2.3 B3：规划面最小工业编译器增强

- Pass2 已稳定输出：
  - `graph_digest`
  - `planning_summary`
  - `canonicalization_summary`
  - `rewrite_summary`
  - `runtime_assertions`
- 后端投影已补齐 typed read model
- 前端详情页 / 结果页已展示上述治理与编译字段

### 2.4 B4：Assertion / Promotion / 前端收口

- runtime 已返回结构化 assertion failure：
  - `failure_code=ASSERTION_FAILED`
  - `assertion_id`
  - `node_id`
  - `message`
  - `details`
- backend 已引入 `AssertionFailureMapper`
  - repairable assertion failure -> `WAITING_USER`
  - fatal assertion failure -> `FAILED`
- success path 已改为：
  - `RUNNING -> ARTIFACT_PROMOTING -> SUCCEEDED`
- promotion 失败会进入 `STATE_CORRUPTED`
- 管理员接口已落地：
  - `POST /tasks/{taskId}/force-revert-checkpoint`
- 前端已补管理员入口：
  - `STATE_CORRUPTED` 下可执行 checkpoint 强制回退

---

## 3. 本轮新增完成项

本轮相较于最初开工时，额外补齐了下面这些关键收口项：

- `/resume` Ack 语义从“过早 ACK”修正为“candidate 持久化后 ACK”
- `MIN_READY` 从“slot 是否存在”收紧为“attachment 元数据可见性 + accepted override”
- 管理员 `force-revert-checkpoint` 前端入口已打通
- 新增 deterministic 验收场景：
  - `assertionfailure`
  - `promotionfailure`
- `week6-e2e.ps1` 已覆盖：
  - success
  - repair
  - fatal
  - cancel
  - assertion failure repair
  - promotion failure -> `STATE_CORRUPTED`
  - `STATE_CORRUPTED` 下 upload/resume 拒绝
  - force-revert 后恢复并再次成功

---

## 4. 已验证结果

### 4.1 本地回归

- `BackEnd`
  - `mvn -q test`
  - 已通过
- `FrontEnd`
  - `npm run build`
  - 已通过
- `Service/planning-pass1`
  - 新增测试已写入仓库
  - 本地默认 Python 环境缺少 `pytest` / `fastapi`
  - 因此本机默认 shell 只做了 `python -m compileall app tests/test_pass1_api.py`

### 4.2 集成验收

- `scripts/week6-e2e.ps1`
  - 已通过
  - 已覆盖 assertion repair / promotion failure / force-revert 主链

验收结果摘要：

- `success => SUCCEEDED`
- `repair => SUCCEEDED`
- `fatal => FAILED`
- `assert => SUCCEEDED`
- `promote => SUCCEEDED`
- `cancel => CANCELLED`

其中 `promote => SUCCEEDED` 的含义是：

- 任务先经过 `promotion failure -> STATE_CORRUPTED`
- 再经过 `force-revert-checkpoint`
- 再 resume 成功

---

## 5. 还没做完的事

下面列的是当前仍值得继续做、但已经不阻塞主链能力的项。

### 5.1 代码 / 自动化缺口

- 补一组更聚焦的 backend 集成测试，直接验证 `resume_txn_json` 的状态流转：
  - `PREPARING -> ACKED -> COMMITTED`
  - `PREPARING -> ROLLED_BACK`
  - `PREPARING/ACKED -> CORRUPTED`
- 补一组针对历史 manifest 空字段兼容的自动化读模型测试：
  - `freeze_status / graph_digest / planning_summary_json` 为空时 detail / manifest / result 仍可读
- 若要把 `Phase2-B` 作为正式冻结基线，还需要形成：
  - 最终验收记录
  - 剩余技术债务清单
  - 明确的冻结点或提交边界

### 5.2 环境 / 工程债务

- 本机默认 Python 环境还不能直接运行 service pytest：
  - 缺 `pytest`
  - 缺 `fastapi`
- Docker Desktop 在脚本 cleanup 阶段仍偶发出现 daemon 连接问题
  - 当前不影响主链业务验收结论
  - 但仍是基础设施层面的稳定性债务

### 5.3 文档与治理收尾

- 还需要把当前落地状态同步回更正式的阶段文档
- 还需要形成一份更短的 `Phase2-B` 收口结论：
  - 哪些已经可以视为完成
  - 哪些仍属于“已知但不阻塞”的债务
  - 哪些项必须进入下一批

---

## 6. 下一批建议顺序

建议按下面顺序继续：

1. 补 `resume_txn_json` 直测与历史 manifest 兼容测试
2. 整理 `Phase2-B` 最终验收记录
3. 冻结当前基线，再决定是否继续往下一轮治理增强推进

---

## 7. 一句话判断

当前 `Phase2-B` 不是“还差很多功能”，而是：

> 主链已成，治理已上线，剩余主要是补测、兼容性证明、环境稳定性和最终冻结收尾。
