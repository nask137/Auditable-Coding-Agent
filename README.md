# Auditable Coding Agent

Auditable Coding Agent 是一个本地运行的可审计编码智能体后端服务。项目目标不是做一个只会聊天的 Agent，而是把编码任务拆成可追踪的步骤：理解任务、检查 workspace、生成计划、调用受控工具、记录文件变更、执行验证命令、处理审批，并生成任务报告。

当前代码对应“阶段 1：单 Agent 执行闭环”MVP。运行时已经具备 Spring Boot 后端、PostgreSQL 持久化、Flyway 建表、固定 Agent Loop、基础文件工具、命令审批、审计事件、任务报告和轻量 CLI。LLM 侧目前使用 `StubLlmGateway`，用于验证 Runtime、权限、审计和报告闭环，尚未接入真实模型。

## 当前能力

- 注册 trusted workspace，并把所有文件和命令操作限制在 workspace 内。
- 创建、启动、取消和查询编码任务。
- 为每次任务创建 AgentRun、AgentStep、AgentAction、Plan 和 PlanItem。
- 通过文件工具执行 `list_files`、`read_file`、`search_text`、`create_file` 和 `apply_patch`。
- 通过命令工具执行受策略控制的 `run_command`。
- 对白名单外命令、敏感文件、高影响文件或大范围修改创建审批请求。
- 记录 AuditEvent、ToolCall、ToolResult、FileChange、CommandExecution、ApprovalRequest、ValidationResult 和 TaskReport。
- 通过 REST API 和 picocli CLI 查看任务状态、事件、变更、审批和报告。

## 技术栈

- Java 25
- Spring Boot 4.0.6
- Maven
- PostgreSQL
- Flyway
- Spring JDBC
- picocli
- JUnit 5 / AssertJ / Mockito

## 项目结构

```text
.
├── docs/                         # 产品定位、MVP 规范、阶段 1 设计和测试指南
│   ├── auditable-coding-agent-plan.md
│   ├── step0/
│   └── step1/
├── src/main/java/com/nask/agent/
│   ├── AgentApplication.java      # Spring Boot 入口
│   ├── action/                    # AgentAction 记录
│   ├── api/                       # 任务观察 API
│   ├── approval/                  # 审批请求和 approve/deny
│   ├── audit/                     # 审计事件
│   ├── cli/                       # picocli 客户端
│   ├── command/                   # 命令策略和命令执行
│   ├── common/                    # 通用配置、枚举、错误处理
│   ├── file/                      # 文件工具、diff 和文件变更记录
│   ├── llm/                       # LLM Gateway 接口和 Stub 实现
│   ├── plan/                      # Plan / PlanItem
│   ├── report/                    # 任务报告
│   ├── run/                       # AgentRun 和固定 Agent Loop
│   ├── step/                      # AgentStep
│   ├── task/                      # CodingTask
│   ├── tool/                      # ToolCall / ToolResult
│   ├── validation/                # 验证结果
│   └── workspace/                 # Workspace 和路径边界保护
├── src/main/resources/
│   ├── application.properties     # 默认配置
│   └── db/migration/              # Flyway schema
├── src/test/java/                 # 单元测试和阶段 1 API 集成测试
└── pom.xml
```

## 前置条件

确认本机具备：

```powershell
java -version
mvn -version
```

项目默认使用 Java 25。后端默认连接本地 PostgreSQL：

```text
jdbc:postgresql://localhost:5432/agent
username: codex
password: codex
```

可以用 Docker 启动一个本地数据库：

```powershell
docker run --name agent-postgres `
  -e POSTGRES_DB=agent `
  -e POSTGRES_USER=codex `
  -e POSTGRES_PASSWORD=codex `
  -p 5432:5432 `
  -d postgres:16
```

如果容器已经存在：

```powershell
docker start agent-postgres
```

## 配置

默认配置位于 `src/main/resources/application.properties`。常用环境变量：

```powershell
$env:AGENT_DATASOURCE_URL='jdbc:postgresql://localhost:5432/agent'
$env:AGENT_DATASOURCE_USERNAME='codex'
$env:AGENT_DATASOURCE_PASSWORD='codex'
```

主要运行时限制：

```text
agent.loop.max-steps=20
agent.loop.max-tool-calls=50
agent.loop.max-file-changes=5
agent.loop.max-patch-lines=300
agent.loop.max-consecutive-failures=3
agent.command.timeout-seconds=120
agent.file.max-read-bytes=200000
```

## 启动服务

```powershell
mvn spring-boot:run
```

服务默认监听：

```text
http://localhost:8080
```

首次启动时 Flyway 会创建阶段 1 所需的表，包括 workspace、task、agent_run、plan、agent_step、tool_call、file_change、command_execution、approval_request、audit_event、validation_result 和 task_report 等。

## CLI 使用

CLI 位于 `com.nask.agent.cli.AgentCli`。先编译并生成运行 classpath：

```powershell
mvn -q "-DskipTests" package
mvn -q dependency:build-classpath "-Dmdep.outputFile=target/classpath.txt"
$AGENT_CP = "target\classes;$(Get-Content target\classpath.txt)"
```

可选：定义一个临时 PowerShell 函数：

```powershell
function agent {
  java -cp $AGENT_CP com.nask.agent.cli.AgentCli @args
}
```

查看帮助：

```powershell
agent --help
agent workspace --help
agent command --help
```

### 完整闭环示例

准备一个测试 workspace：

```powershell
$workspacePath = "D:\tmp\agent-demo"
New-Item -ItemType Directory -Force $workspacePath | Out-Null
Set-Content "$workspacePath\README.md" "agent demo workspace"
```

注册 workspace：

```powershell
$workspaceJson = agent workspace add $workspacePath
$workspace = $workspaceJson | ConvertFrom-Json
$workspaceId = $workspace.id
```

当前 `StubLlmGateway` 会建议执行 `java -version` 作为验证命令。如果希望任务直接完成，可以先加入命令白名单：

```powershell
agent command allow --workspace $workspaceId --exec java --args "-version"
```

提交并启动任务：

```powershell
$runJson = agent run "create an audited note and validate" --workspace $workspaceId
$run = $runJson | ConvertFrom-Json
$taskId = $run.taskId
```

查看状态、事件、文件变更和报告：

```powershell
agent status $taskId
agent events $taskId
agent diff $taskId
agent report $taskId
```

在当前 Stub 行为下，Agent 会在 workspace 中创建 `AGENT_TASK_NOTE.md`，记录一次 FileChange，执行或申请执行 `java -version`，并生成 Markdown 格式的 TaskReport。

### 审批流程

如果没有提前加入 `java -version` 白名单，任务会在验证命令处进入 `WAITING_APPROVAL`。

查看待审批请求：

```powershell
agent approvals
```

批准：

```powershell
agent approve <approvalId>
```

拒绝：

```powershell
agent deny <approvalId>
```

批准后后端会恢复同步阶段 1 Loop；拒绝后关联任务会失败，并记录审批拒绝事件。

## REST API

主要 API：

```text
POST /api/workspaces
GET  /api/workspaces
GET  /api/workspaces/{workspaceId}

POST /api/tasks
GET  /api/tasks/{taskId}
POST /api/tasks/{taskId}/start
POST /api/tasks/{taskId}/cancel

GET  /api/runs/{runId}
GET  /api/runs/{runId}/plan
GET  /api/runs/{runId}/steps

GET  /api/tasks/{taskId}/events
GET  /api/tasks/{taskId}/changes
GET  /api/tasks/{taskId}/report

GET  /api/approvals
GET  /api/approvals/{approvalId}
POST /api/approvals/{approvalId}/approve
POST /api/approvals/{approvalId}/deny

GET    /api/workspaces/{workspaceId}/command-policies
POST   /api/workspaces/{workspaceId}/command-policies
DELETE /api/command-policies/{policyId}
```

示例：注册 workspace。

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/workspaces `
  -ContentType 'application/json' `
  -Body (@{
    name = 'demo'
    rootPath = 'D:\tmp\agent-demo'
    trusted = $true
  } | ConvertTo-Json)
```

## 测试

运行测试：

```powershell
mvn test
```

当前测试覆盖：

- workspace 路径边界、路径穿越和 `.git` 写入保护。
- 敏感文件识别和 symlink 越界拦截。
- diff 和 hash 生成。
- 审批请求消费规则。
- 阶段 1 API 集成闭环：注册 workspace、添加命令白名单、启动任务、创建 `AGENT_TASK_NOTE.md`、记录审计事件、记录文件变更并生成报告。

集成测试依赖可用的 PostgreSQL 数据源；默认读取 `application.properties` 中的连接配置。

## 设计文档

建议按顺序阅读：

- `docs/auditable-coding-agent-plan.md`：总体建设计划。
- `docs/step0/product-positioning.md`：产品定位。
- `docs/step0/mvp-specification.md`：第一版 MVP 规范。
- `docs/step0/core-domain-model.md`：核心领域模型。
- `docs/step0/permission-model.md`：权限模型。
- `docs/step0/audit-log-model.md`：审计日志模型。
- `docs/step1/phase1-technical-design.md`：阶段 1 技术设计。
- `docs/step1/phase1-work-plan.md`：阶段 1 工作计划。
- `docs/step1/phase1-cli-test-guide.md`：CLI 手工测试指南。

## 当前限制

- 当前 LLM 实现是 `StubLlmGateway`，固定生成三步计划，不具备真实代码理解和自动修复能力。
- 阶段 1 Agent Loop 是同步执行；后台任务队列、WebSocket/SSE 推送和多实例协调尚未实现。
- CLI 输出原始 JSON，尚未做表格化、分页、高亮或交互式审批。
- 目前没有 Web 控制台、IDE 插件、多 Agent、长期记忆、RAG 或工作流 DSL。
- 文件修改能力已有审计和审批基础，但 Stub Loop 目前只演示创建 `AGENT_TASK_NOTE.md`，不会自动修改业务源码。

## 后续方向

项目规划按阶段推进：

```text
阶段 1：单 Agent 执行闭环
阶段 2：可审计运行时
阶段 3：状态机与工作流内核
阶段 4：项目记忆与代码理解
阶段 5：可视化编排与回放
阶段 6：多 Agent、插件化与产品化
```

短期最重要的下一步是替换 `StubLlmGateway`，接入真实模型，并在保持 Runtime 权限、审批和审计约束不变的前提下，让模型输出结构化任务理解、计划和工具动作。
