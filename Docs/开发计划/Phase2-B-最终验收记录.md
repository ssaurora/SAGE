# Phase2-B 最终验收记录

更新日期：2026-03-27

## 1. 验收范围

本次验收针对当前最新工作树快照，覆盖以下内容：

- `BackEnd` 控制层强治理恢复链
- `analysis_manifest` 冻结与版本解耦
- `Service/planning-pass1` 最小工业编译器增强
- `AssertionFailureMapper`、`ARTIFACT_PROMOTING`、`STATE_CORRUPTED` 主链
- `FrontEnd` 对治理读模型的展示与管理员回退入口
- `Week6` 验收脚本中新增的 assertion repair / promotion failure / force-revert 场景

---

## 2. 执行结果

### 2.1 单体回归

#### `BackEnd`

命令：

`mvn -q test`

结果：

- 通过

说明：

- backend 已覆盖本轮新增的治理字段、投影、断言映射、`MIN_READY` 规则与 `resume_txn_json` 状态流测试

#### `BackEnd` 治理专项

命令：

`mvn -q -Dtest=TaskServiceGovernanceTest test`

结果：

- 通过

说明：

- 已验证：
  - `PREPARING -> ACKED -> COMMITTED`
  - `PREPARING -> ROLLED_BACK`
  - commit conflict -> `CORRUPTED` 分支
  - 历史 manifest 空治理字段在 `manifest/result` 读模型中的兼容行为

#### `FrontEnd`

命令：

`npm run build`

结果：

- 通过

说明：

- 当前治理字段展示、管理员 `force-revert-checkpoint` 入口和结果页展示未引入构建回退

#### `Service/planning-pass1`

命令：

`python -m compileall app tests/test_pass1_api.py`

结果：

- 通过

说明：

- 本机默认 Python 环境缺少 `pytest` / `fastapi`，因此本轮在当前机器上未直接执行 service pytest
- service 侧 deterministic 场景钩子与测试文件已落库

---

### 2.2 集成回归

#### `Week6`

命令：

`powershell -ExecutionPolicy Bypass -File .\scripts\week6-e2e.ps1`

结果：

- `Week6 E2E passed`
- `success => SUCCEEDED`
- `repair => SUCCEEDED`
- `fatal => FAILED`
- `assert => SUCCEEDED`
- `promote => SUCCEEDED`
- `cancel => CANCELLED`

业务结论：

- `assertion failure -> WAITING_USER -> upload/fix -> resume -> SUCCEEDED` 主链通过
- `promotion failure -> STATE_CORRUPTED -> force-revert-checkpoint -> resume -> SUCCEEDED` 主链通过
- `STATE_CORRUPTED` 下 upload / resume 拒绝已得到验收

---

## 3. 对照 `B1 ~ B4` 的最终判断

| 项目 | 最终判断 | 说明 |
|---|---|---|
| `B1` 控制面强治理恢复链 | 已完成 | `resume_txn_json`、`RESUMING`、`STATE_CORRUPTED`、严格 `MIN_READY` 已进入主链 |
| `B2` Manifest 冻结与版本解耦 | 已完成 | candidate / freeze 拆分已落地，治理字段与迁移已补齐 |
| `B3` 规划面最小工业编译器增强 | 已完成 | `graph_digest / planning_summary / canonicalization_summary / rewrite_summary / runtime_assertions` 已贯通 service/backend/frontend |
| `B4` Assertion / Promotion / 前端收口 | 已完成 | repairable assertion 映射、`ARTIFACT_PROMOTING`、promotion failure 治理、管理员回退入口均已落地 |

---

## 4. 基础设施与环境备注

本次验收同时记录以下非阻塞性问题：

- 本机默认 Python 环境缺少：
  - `pytest`
  - `fastapi`
- Docker Desktop 在脚本 cleanup 阶段仍偶发出现 daemon 连接问题

这次验收的判定如下：

- 以上问题属于 **环境与基础设施债务**
- 不构成 `Phase2-B` 主链能力验收失败
- 需要在后续工程维护中单独处理

---

## 5. 最终阶段结论

本次验收后，当前可以作出以下判断：

> `Phase2-B` 已完成。

更精确一点说：

- 控制层已从“可跑通”进入“带治理语义的恢复与冻结主线”
- manifest、checkpoint、inventory、planning revision 已进入统一版本语义
- 规划编译结果已成为后端 authority facts 的一部分，并已进入读模型展示
- assertion failure 与 promotion failure 已成为一等公民治理场景
- 当前最新快照已经补齐后端治理专项测试、前端构建回归和 `Week6` 集成验收

---

## 6. 关闭与冻结决定

基于本次验收结果，现作出以下项目结论：

1. 以本验收记录为界，`Phase2-B` 正式关闭
2. 当前工作树所体现的治理主线，作为 `Phase2-B` 冻结基线
3. 在未形成下一轮正式任务单前，不再以 `Phase2-B` 名义继续追加零散治理改动

冻结基线的具体边界，以配套文档 [Phase2-B-冻结基线说明.md](/e:/paper_project/SAGE/Docs/开发计划/Phase2-B-冻结基线说明.md) 为准。

---

## 7. 进入下一阶段前的保留事项

虽然 `Phase2-B` 已完成，但以下事项仍保留为后续约束或债务，不视为本阶段阻塞项：

- service pytest 运行环境未在当前机器上补齐
- Docker cleanup 阶段稳定性问题

---

## 8. 建议

从项目节奏上看，当前已经完成的动作是：

1. 以本验收记录为界，关闭 `Phase2-B`
2. 冻结当前治理基线

接下来仍需要单独决策的是：

3. 是否进入下一轮治理增强或能力扩展

如果继续开发，不建议再把 `Phase2-B` 主线重新打开做零散补丁；应以新阶段或新任务单重新立项。
