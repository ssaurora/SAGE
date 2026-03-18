# 第 3 周：通执行链

## 本周目标

把绑定和参数草案转成可执行对象，并提交真实 analysis job。

---

## 前端团队

本周任务：

- 在详情页增加 job 区块：
    
    - `job_id`
        
    - job 状态
        
    - 运行中展示
        
- 增加执行阶段时间线
    

交付物：

- 一个能看到 task 与 job 关联的详情页
    

技术栈：

- React / Next.js
    
- TypeScript
    
- SSE 或轮询
    

---

## Java / Spring Boot 团队

本周任务：

- 接入 Planning Pass 2
    
- 控制面拿到 `materialized_execution_graph`
    
- 建立 `task_id -> job_id` 映射
    
- 开 job 提交接口
    
- 查询 job 状态并回写 `task_state`
    

交付物：

- job 提交与状态同步链路
    

技术栈：

- Spring Boot
    
- PostgreSQL
    

---

## Python 团队

本周任务：

- 实现 Planning Pass 2
    
- 输出：
    
    - `materialized_execution_graph`
        
    - `runtime_assertions`
        
    - `planning_summary`
        
- 实现 Worker 原型
    
- 在 Docker 中运行 analysis job
    
- 回传 heartbeat
    
- 形成最小结果对象
    

交付物：

- Pass 2 服务
    
- Worker 原型
    
- job status 回传逻辑
    

技术栈：

- Python
    
- Pydantic
    
- NetworkX 或轻量图表达
    
- Docker
    
- InVEST
    

---

## 数据库与存储团队

本周任务：

- 建立 `job_record`
    
- 设计 job 状态字段
    
- 建立 task 与 job 的关联查询能力
    
- 设计最小结果对象的存储结构
    

交付物：

- `job_record` 表
    
- 结果对象摘要表或字段方案
    

技术栈：

- PostgreSQL
    

---

## 第 3 周联合验收标准

周末必须看到：

1. Pass 2 真实存在并输出执行对象
    
2. 控制面可以提交一个 analysis job
    
3. job 在 Docker 环境中真实运行
    
4. 前端能看到 `task_id -> job_id` 对应关系
    
5. job 能从 `ACCEPTED` 推进到 `RUNNING` 再到终态
    
6. 至少能得到最小结果对象，而不是只剩日志文件
    

---