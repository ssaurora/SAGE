# SAGE Week1 主链路 MVP

本仓库按 Week1“立骨架”目标实现最小闭环：

`登录 -> 创建任务 -> 任务落库 -> 状态推进 -> 事件记录 -> Pass1 结构化输出 -> 前端可见`

## 目录结构

- `FrontEnd`：Next.js App Router + TypeScript
- `BackEnd`：Spring Boot + MyBatis + Flyway + PostgreSQL + JWT
- `Service/planning-pass1`：FastAPI Pass1 服务（固定 `water_yield_v1`）

## 默认端口

- FrontEnd: `3000`
- BackEnd: `8080`
- Pass1 Service: `8001`
- PostgreSQL: `5432`

## 1. 启动 PostgreSQL

创建数据库：

```sql
CREATE DATABASE sage;
```

## 2. 启动 Pass1 服务（必须先启动）

```bash
cd Service/planning-pass1
conda run -n sage-cognitive python -m pip install -r requirements.txt
conda run -n sage-cognitive uvicorn app.main:app --host 0.0.0.0 --port 8001
```

### Pass1 测试

```bash
cd Service/planning-pass1
conda run -n sage-cognitive pytest
```

## 3. 启动 BackEnd

先复制并修改环境变量：

```bash
cd BackEnd
copy .env.example .env
```

然后启动：

```bash
cd BackEnd
mvn spring-boot:run
```

Flyway 会自动建表并写入演示用户：

- username: `demo`
- password: `demo123`

## 4. 启动 FrontEnd

```bash
cd FrontEnd
npm install
npm run dev
```

访问：`http://localhost:3000/login`

## 5. Docker 独立启动 Pass1

```bash
cd Service/planning-pass1
docker build -t sage-pass1:week1 .
docker run --rm -p 8001:8001 sage-pass1:week1
```

## 关键 API

### 认证

- `POST /auth/login`
- `GET /auth/me`

### 任务

- `POST /tasks`
- `GET /tasks/{taskId}`
- `GET /tasks/{taskId}/events`

### 规划

- `POST /planning/pass1`（由 BackEnd 调用 Service）

## Week1 联合验收检查清单

- [x] 用户可登录系统
- [x] 前端可创建任务
- [x] 后端生成并落库 `task_id`
- [x] 状态与事件可展示
- [x] Pass1 返回 `selected_template` / `logical_input_roles` / `slot_schema_view` / `graph_skeleton`
- [x] Python 镜像可独立启动
- [x] 正式链路不依赖口头模板约定
