# 第 4 周：跑通 Phase 0

## 本周目标

完成自然语言到真实 `water_yield` 结果的完整可演示闭环。

---

## 前端团队

本周任务：

- 做结果页
    
- 展示：
    
    - 结果摘要
        
    - 主要输出
        
    - 解释文本
        
    - 失败信息
        
- 增加 cancel 按钮
    

交付物：

- 完整结果页
    
- cancel 交互
    

技术栈：

- React / Next.js
    
- TypeScript
    

---

## Java / Spring Boot 团队

本周任务：

- 提供 `/result`
    
- 提供 `/cancel`
    
- 串起 Final Explanation 调用
    
- 控制任务从 `RUNNING` 进入 `RESULT_PROCESSING`、`SUCCEEDED / FAILED / CANCELLED`
    

交付物：

- 结果接口
    
- cancel 接口
    
- 最终状态收口逻辑
    

技术栈：

- Spring Boot
    
- PostgreSQL
    

---

## Python 团队

本周任务：

- 实现 `result_bundle` 雏形
    
- 基于结构化结果生成解释输入
    
- 实现 cancel 真终止
    
- 保证运行中 job 可被停止
    

交付物：

- `result_bundle`
    
- final explanation 输入整理
    
- cancel 实现
    

技术栈：

- Python
    
- LLM 调用层
    
- Docker
    
- InVEST
    

---

## 数据库与存储团队

本周任务：

- 设计 `result_bundle` 摘要存储
    
- 保存失败信息和关键指标摘要
    
- 确定日志、输出文件与任务的映射关系
    

交付物：

- 结果摘要落库方案
    
- 失败信息记录方案
    

技术栈：

- PostgreSQL
    
- 对象存储或约定文件目录
    

---

## 第 4 周联合验收标准

周末必须看到：

1. 用户一句自然语言可触发完整 `water_yield` 链路
    
2. 链路完成：Pass 1 → Pass B → Pass 2 → validate → job → result → explanation
    
3. 前端展示的是结构化结果与解释，而不是路径堆
    
4. cancel 可以真正终止 job
    
5. 整条演示链路完全运行在固定 Docker 环境中
    
6. **Phase 0 到本周结束正式完成**
    

---
