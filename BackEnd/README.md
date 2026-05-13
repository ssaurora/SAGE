# SAGE BackEnd

SAGE BackEnd 是 SAGE 治理式分析系统的后端控制面，负责认证、任务编排、会话协作、状态流转、审计追踪、结果查询以及与规划/执行服务的集成。

当前后端不是一个简单的接口转发层。它已经承担了从自然语言任务创建到治理校验、等待用户补充、恢复执行、结果归档和审计查询的主链路。

## 目前已经完成的内容

### 1. 用户认证与安全

- 基于 Spring Security 和 JWT 的登录认证。
- 提供 `POST /auth/login` 和 `GET /auth/me`。
- 默认 Flyway 初始化 demo 用户：`demo / demo123`。
- 请求侧支持 Bearer Token 鉴权。

### 2. 治理式任务主链路

后端已经实现任务从创建到终态的核心编排：

```text
POST /tasks
-> goal_route
-> pass1
-> passB
-> primitive validation
-> pass2
-> submit_job
-> job sync
-> result / manifest / artifacts / audit
```

已支持的任务能力包括：

- 创建任务。
- 查询任务详情、事件和流式状态。
- 查询 manifest、result、runs、artifacts、audit、catalog、contract。
- 取消任务。
- 上传任务附件。
- 从 `WAITING_USER` 恢复任务。
- 强制回滚 checkpoint。

### 3. Repair / Resume 修复闭环

当前系统已经支持输入不完整时的治理式暂停与恢复：

- 缺少必要输入时进入 `WAITING_USER`。
- `waiting_context_json` 作为等待用户补充时的结构化视图。
- 用户可通过上传附件或 override 补充输入。
- `/resume` 只允许从 `WAITING_USER` 状态进入。
- resume 会重新进入验证与派发链路，而不是绕过治理流程。
- 重复 resume 具备幂等保护。

### 4. 结果、运行和审计追踪

后端已经持久化并提供查询：

- analysis manifest
- result bundle record
- artifact index
- job record
- task attempt
- workspace registry
- audit record
- event log
- task catalog snapshot

这使得一次分析运行的配置、输入、输出、运行证据和审计记录都可以回查。

### 5. Catalog-first 治理切片

当前已经有一个被主链路使用的 catalog-first 能力：

- 附件 catalog 投影。
- catalog consistency 投影。
- task-level catalog snapshot。
- manifest/detail/result/audit 中的 catalog governance view。
- resume 阶段的 catalog readiness 检查。

Catalog 目前不是完整独立平台，但已经参与任务规划、等待、恢复、manifest 和结果查询。

### 6. Contract-first 治理切片

后端已经将执行契约纳入主流程：

- 执行 contract 组装。
- contract version / fingerprint。
- frozen/current contract 身份比较。
- resume 时的 contract mismatch 报告。
- contract governance 查询视图。

当前涉及的契约点包括：

- `checkpoint_resume_ack`
- `validate_bindings`
- `validate_args`
- `submit_job`
- `cancel_job`
- `query_job_status`
- `collect_result_bundle`
- `index_artifacts`
- `record_audit`

### 7. 场景和会话协作接口

后端已经提供面向新前端的场景与会话投影：

- 场景列表。
- 场景详情。
- 场景会话投影。
- 会话消息列表。
- 发送会话消息。
- 上传场景会话附件。
- demo live simulation reset / confirm。

这些接口用于支撑 `sage-web` 的场景首页、分析会话、工作台、治理和结果页。

### 8. Demo 数据和迁移脚本

Flyway migration 已经包含：

- 用户与认证表。
- 任务核心表。
- 输入链路、执行链路、结果和取消。
- Week5 repair loop。
- Phase0 manifest 和 goal route。
- Week6 traceability。
- Phase2/Phase3 catalog 和 contract 治理字段。
- session collaboration layer。
- demo scene projection seed。
- urban cooling / water yield demo session seed。

## 技术栈

- Java 17
- Spring Boot 3.4.4
- Spring Security
- JWT
- MyBatis
- Flyway
- PostgreSQL
- Spring Boot Actuator
- Maven

## 目录结构

```text
BackEnd/
├─ pom.xml
├─ src/main/java/com/sage/backend/
│  ├─ auth/               # 登录、当前用户
│  ├─ security/           # JWT 与安全过滤器
│  ├─ task/               # 任务创建、编排、治理、查询
│  ├─ session/            # 分析会话与消息
│  ├─ scene/              # 场景投影与 demo session
│  ├─ cognition/          # cognition 服务客户端
│  ├─ planning/           # pass1/pass2 客户端
│  ├─ validationgate/     # primitive validation 客户端
│  ├─ execution/          # job runtime 客户端
│  ├─ repair/             # repair proposal 与 resume 支撑
│  ├─ audit/              # 审计写入
│  ├─ event/              # 事件日志
│  ├─ mapper/             # MyBatis mapper
│  └─ model/              # 数据模型
├─ src/main/resources/
│  ├─ application.yml
│  └─ db/migration/       # Flyway 迁移和 demo seed
└─ src/test/java/         # 单元测试
```

## 主要 API

### Auth

```text
POST /auth/login
GET  /auth/me
```

### Task

```text
POST /tasks
GET  /tasks/{taskId}
GET  /tasks/{taskId}/events
GET  /tasks/{taskId}/stream
GET  /tasks/{taskId}/manifest
GET  /tasks/{taskId}/result
GET  /tasks/{taskId}/runs
GET  /tasks/{taskId}/artifacts
GET  /tasks/{taskId}/audit
GET  /tasks/{taskId}/catalog
GET  /tasks/{taskId}/contract
POST /tasks/{taskId}/cancel
POST /tasks/{taskId}/attachments
POST /tasks/{taskId}/resume
POST /tasks/{taskId}/force-revert-checkpoint
```

### Scene

```text
GET  /scenes
GET  /scenes/{sceneId}
GET  /scenes/{sceneId}/session
GET  /scenes/{sceneId}/session/messages
POST /scenes/{sceneId}/session/messages
POST /scenes/{sceneId}/session/attachments
POST /scenes/{sceneId}/session/demo-live-simulation/reset
POST /scenes/{sceneId}/session/demo-live-simulation/confirm
```

### Session

```text
POST /sessions
GET  /sessions
GET  /sessions/{sessionId}
GET  /sessions/{sessionId}/messages
POST /sessions/{sessionId}/messages
POST /sessions/{sessionId}/attachments
GET  /sessions/{sessionId}/stream
```

### Deprecated Compatibility

```text
GET  /result?task_id=...
POST /cancel?task_id=...
```

## 本地启动

### 方式一：只启动后端

前置条件：

- JDK 17
- Maven
- PostgreSQL

默认数据库配置：

```yaml
url: jdbc:postgresql://localhost:5432/sage
username: postgres
password: postgres
```

启动：

```powershell
cd E:\Github\SAGE-ALL\SAGE\BackEnd
mvn spring-boot:run
```

健康检查：

```text
http://localhost:8080/actuator/health
```

### 方式二：使用根目录 Docker Compose

在 `SAGE` 根目录执行：

```powershell
cd E:\Github\SAGE-ALL\SAGE
powershell -ExecutionPolicy Bypass -File .\scripts\compose-up.ps1 -Build
```

该方式会启动 PostgreSQL、Redis、Service、BackEnd 和 FrontEnd。

## 常用环境变量

```text
SERVER_PORT=8080
DB_URL=jdbc:postgresql://localhost:5432/sage
DB_USERNAME=postgres
DB_PASSWORD=postgres
JWT_SECRET=sage-week1-hs256-secret-key-change-me-12345
JWT_EXPIRATION_HOURS=8
PASS1_BASE_URL=http://localhost:8001
JOB_SYNC_FIXED_DELAY_MS=2000
SAGE_UPLOAD_ROOT=BackEnd/runtime/uploads
SAGE_MAX_UPLOAD_FILE_SIZE=64MB
SAGE_MAX_UPLOAD_REQUEST_SIZE=128MB
```

## 测试

```powershell
cd E:\Github\SAGE-ALL\SAGE\BackEnd
mvn test
```

当前测试覆盖了 planning fact、repair、scene projection、session service、task governance、catalog projector、contract guard、execution contract、query projection 等关键支撑类。

## 当前边界

- 后端负责工作流、状态、冻结、审计、治理和服务编排。
- 规划和执行细节由 `Service/planning-pass1` 等服务提供。
- 前端只渲染和交互，不拥有任务状态机语义。
- Catalog 和 Contract 已进入主链路，但还不是完整独立的产品域。
