# Phase2-B 测试收口记录

更新时间：2026-03-27

## 本轮完成

- 新增后端治理测试文件：
  - `BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java`
- 新增并通过的覆盖点：
  - `resume_txn_json` 成功链：`PREPARING -> ACKED -> COMMITTED`
  - `resume_txn_json` recoverable validation：`PREPARING -> ROLLED_BACK`
  - `resume_txn_json` commit conflict：控制层进入 `CORRUPTED` 分支，并对外返回 `502 Resume pipeline failed`
  - 历史 manifest 空治理字段在 `manifest/result` 读模型中的兼容行为

## 验证结果

- `BackEnd`: `mvn -q -Dtest=TaskServiceGovernanceTest test` 通过
- `BackEnd`: `mvn -q test` 全量通过

说明：
- 测试日志中出现的 `Task resume pipeline failed for task task_resume` 是 commit conflict 场景下的预期日志，不代表测试失败。

## 当前剩余事项

- 整理 `Phase2-B` 最终验收记录
- 记录并处理环境债务：
  - 本机 Python 默认环境缺 `pytest`
  - 本机 Python 默认环境缺 `fastapi`
  - Docker cleanup 阶段仍有偶发 daemon 连接问题
- 明确是否以当前工作树作为 `Phase2-B` 冻结基线

## 当前判断

`Phase2-B` 现在剩下的已经不是主链能力缺口，而是文档冻结和环境稳定性收口。
