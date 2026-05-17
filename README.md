# Auditable Coding Agent

Auditable Coding Agent 是一个本地运行的可审计编码智能体后端服务。项目目标不是做一个只会聊天的 Agent，而是把编码任务拆成可追踪的步骤：理解任务、检查 workspace、生成计划、调用受控工具、记录文件变更、执行验证命令、处理审批，并生成任务报告。

当前代码已推进到“阶段 4：项目记忆与代码理解”的节点化运行时基础版本。运行时已经具备 Spring Boot 后端、PostgreSQL 持久化、Flyway 建表、基础文件工具、Git 只读工具、命令审批、审计事件、结构化失败记录、恢复策略、用户介入请求、任务报告、轻量 CLI，以及可查询的 WorkflowDefinition、WorkflowNodeExecution 和 WorkflowEdgeDecision。`coding-agent` 不再通过固定 Loop 包装执行，而是由 Workflow Runtime 按节点和边调度任务理解、workspace 检查、项目记忆、代码理解、计划、计划项执行、验证、报告和结束节点。LLM 侧默认使用 `StubLlmGateway` 验证 Runtime 闭环，也可以通过 HTTP 网关接入 DeepSeek/OpenAI-compatible 模型，让模型只输出结构化任务理解、计划和工具动作意图。

## 当前能力

- 注册 trusted workspace，并把所有文件和命令操作限制在 workspace 内。
- 创建、启动、取消和查询编码任务。
- 为每次任务创建 AgentRun、AgentStep、AgentAction、Plan 和 PlanItem。
- 通过文件工具执行 `list_files`、`read_file`、`search_text`、`create_file` 和 `apply_patch`。
- 通过 Git 只读工具执行 `git_status` 和 `git_diff`，用于审计修改结果。
- 通过命令工具执行受策略控制的 `run_command`。
- 对白名单外命令、敏感文件、高影响文件或大范围修改创建审批请求。
- 记录 AuditEvent、ToolCall、ToolResult、FileChange、CommandExecution、ApprovalRequest、ValidationResult 和 TaskReport。
- 记录 RuntimeFailure，并在模型输出、工具动作或验证被 Runtime 拒绝时选择重试、重新规划、请求用户介入或失败。
- 创建 UserInputRequest，让任务进入 `WAITING_USER_INPUT` 并在用户回答后恢复运行。
- 通过节点化 Workflow Runtime 运行 `coding-agent`、`review-agent` 和 `test-agent`。
- 扫描 workspace 并持久化项目画像、扫描记录、文档摘要、任务报告摘要、Java outline 和代码符号。
- 通过 `PROJECT_MEMORY` 和 `CODE_UNDERSTANDING` 节点为工作流注入项目画像、检索上下文、代码 outline 和符号搜索结果。
- 对任务经验写入项目记忆建立提案和审批流程，批准后才持久化为可检索记忆。
- 记录工作流节点执行和边选择，任务报告包含 Workflow section。
- 在节点执行异常、工具拒绝、模型输出失败和验证失败时进入统一失败或恢复路径，避免运行卡在 `RUNNING`。
- 通过 REST API 和 picocli CLI 查看任务状态、事件、变更、失败、审批、用户输入请求、报告、项目画像、符号、检索记录和记忆提案。

## 技术栈

- Java 25
- Spring Boot 4.0.6
- Maven
- PostgreSQL
- Flyway
- Spring JDBC
- picocli
- React 19 / Vite / TypeScript
- JUnit 5 / AssertJ / Mockito

## 项目结构

```text
.
├── docs/                         # 当前保留的项目计划和 CLI 使用指南
│   ├── auditable-coding-agent-plan.md
│   └── cli-usage-guide.md
├── src/main/java/com/nask/agent/
│   ├── AgentApplication.java      # Spring Boot 入口
│   ├── action/                    # AgentAction 记录
│   ├── api/                       # 任务观察 API
│   ├── approval/                  # 审批请求和 approve/deny
│   ├── audit/                     # 审计事件
│   ├── cli/                       # picocli 客户端
│   ├── command/                   # 命令策略和命令执行
│   ├── common/                    # 通用配置、枚举、错误处理
│   ├── conversation/              # CLI 会话和 transcript 记录
│   ├── file/                      # 文件工具、diff 和文件变更记录
│   ├── git/                       # Git 只读工具
│   ├── llm/                       # LLM Gateway 接口和 Stub 实现
│   ├── memory/                    # 项目扫描、画像、索引、检索和记忆写入审批
│   ├── permission/                # CLI 权限预设和本机配置
│   ├── plan/                      # Plan / PlanItem
│   ├── report/                    # 任务报告
│   ├── run/                       # AgentRun 生命周期和 legacy loop 接口
│   ├── runtime/                   # 失败分类、恢复策略和用户介入
│   ├── step/                      # AgentStep
│   ├── task/                      # CodingTask
│   ├── tool/                      # ToolCall / ToolResult
│   ├── validation/                # 验证结果
│   ├── workflow/                  # 节点化工作流定义、调度和执行记录
│   └── workspace/                 # Workspace 和路径边界保护
├── src/main/resources/
│   ├── application.properties     # 默认配置
│   ├── application-dev.properties # 本地开发 profile 覆盖
│   ├── application-test.properties # 测试 profile 覆盖
│   └── db/migration/              # Flyway schema
├── src/test/java/                 # 单元测试和阶段 1 API 集成测试
├── tools/
│   └── mock_llm_server.py         # 本地 HTTP LLM mock 服务
├── web-dashboard/                 # React/Vite Web 控制台
│   ├── src/
│   ├── package.json
│   └── vite.config.ts
└── pom.xml
```

## 前置条件

确认本机具备：

```powershell
java -version
mvn -version
```

项目默认使用 Java 25。后端连接 PostgreSQL，主配置通过环境变量提供数据源：

```text
DATASOURCE_URL=jdbc:postgresql://localhost:5432/auditable_agent
DATASOURCE_USERNAME=username
DATASOURCE_PASSWORD=password
```

可以用 Docker 启动一个本地数据库：

```powershell
docker run --name agent-postgres `
  -e POSTGRES_DB=auditable_agent `
  -e POSTGRES_USER=username `
  -e POSTGRES_PASSWORD=password `
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
$env:DATASOURCE_URL='jdbc:postgresql://localhost:5432/auditable_agent'
$env:DATASOURCE_USERNAME='username'
$env:DATASOURCE_PASSWORD='password'
```

主要运行时限制：

```text
agent.loop.max-steps=20
agent.loop.max-tool-calls=50
agent.loop.max-file-changes=5
agent.loop.max-patch-lines=300
agent.loop.max-consecutive-failures=3
agent.loop.max-model-retries=2
agent.loop.max-replan-attempts=2
agent.loop.max-user-input-requests-per-run=3
agent.command.timeout-seconds=120
agent.file.max-read-bytes=200000
```

LLM 默认 provider 是 `stub`。接入 DeepSeek V4 Pro 时配置：

```powershell
$env:AGENT_LLM_PROVIDER='http'
$env:AGENT_LLM_BASE_URL='https://api.deepseek.com'
$env:AGENT_LLM_API_KEY='<your-api-key>'
$env:AGENT_LLM_MODEL='deepseek-v4-pro'
$env:AGENT_LLM_THINKING_ENABLED='false'
$env:AGENT_LLM_REASONING_EFFORT='high'
```

HTTP 网关调用 `/chat/completions`，使用 JSON output mode。DeepSeek thinking mode 默认禁用，可通过 `AGENT_LLM_THINKING_ENABLED=true` 开启，或用 `AGENT_LLM_REASONING_EFFORT=high|max` 调整 effort。模型只返回结构化意图，实际文件和命令操作仍经过 Runtime 校验、审批和审计。

## 启动后端服务

```powershell
mvn spring-boot:run
```

服务默认监听：

```text
http://localhost:8080
```

首次启动时 Flyway 会创建运行时所需的表，包括 workspace、task、agent_run、plan、agent_step、tool_call、file_change、command_execution、approval_request、audit_event、validation_result、task_report、runtime_failure、user_input_request、workflow_definition、workflow_node_execution 和 workflow_edge_decision 等。

项目扫描默认预算可通过配置调整：

```properties
agent.project-scan.max-files=2000
agent.project-scan.max-file-bytes=262144
agent.project-scan.max-total-bytes=10485760
```

## 启动 Web Dashboard

Web 控制台位于 `web-dashboard/`，用于查看 workspace、任务、运行详情、工作流审计、上下文、记忆、CLI 会话和本机设置。

```powershell
cd web-dashboard
npm install
npm run dev
```

Vite 默认监听：

```text
http://localhost:5173
```

## CLI 使用

CLI 位于 `com.nask.agent.cli.AgentCli`。默认运行 `agent` 会进入 Codex 风格的交互式终端会话；原有 REST 包装命令仍保留为显式子命令。更完整的说明见 `docs/cli-usage-guide.md`。先编译并生成运行 classpath：

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
agent tui --help
agent workspace --help
agent command --help
```

### 交互式 TUI

启动交互会话：

```powershell
agent
```

也可以直接带首条提示：

```powershell
agent "review this project and explain the risky files"
agent tui "create an audited note and validate"
```

首次提交任务时，TUI 会把启动 `agent` 的当前目录作为 workspace root。若该目录已注册，会直接复用对应 workspace；若还未注册，会先询问是否信任并注册该目录。会话 transcript 会写入：

```text
%USERPROFILE%\.auditable-agent\sessions\<session-id>.jsonl
```

常用 slash commands：

```text
/status        查看当前 base URL、workspace、权限预设、task/run 状态
/plan          查看当前 run 的计划项
/diff          查看当前 run 记录的文件变更和 diff 摘要
/approvals     查看当前 run 的审批请求，或全局待审批请求
/permissions   查看或设置权限预设：read-only、workspace-write、full-auto
/workspace     查看当前目录解析出的 workspace，或为本终端会话临时指定 workspace UUID
/resume last   恢复最近会话
/new           在当前终端会话内开始新对话
/clear         清屏
/exit          退出
```

权限预设在 V1 中映射到现有可审计 runtime 行为：

```text
read-only：编码请求会转为 review-agent，避免写 workspace。
workspace-write：默认编码模式，写入仍受 WorkspacePathGuard、审批和审计约束。
full-auto：只复用已配置的命令策略，不提供绕过审批和 workspace 保护的 yolo 模式。
```

Dashboard 的“设置”和“CLI 会话”页面会读取同一个本机后端配置目录，用于可视化配置和查看最近 CLI 会话摘要。

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

提交并启动任务，默认使用 `coding-agent` 工作流：

```powershell
$runJson = agent run "create an audited note and validate" --workspace $workspaceId
$run = $runJson | ConvertFrom-Json
$runId = $run.id
$taskId = $run.taskId
```

查看状态、事件、文件变更和报告：

```powershell
agent status $taskId
agent events $taskId
agent failures $taskId
agent workflow-status $runId
agent workflow-path $runId
agent diff $taskId
agent report $taskId
```

在当前 Stub 行为下，Agent 会在 workspace 中创建 `AGENT_TASK_NOTE.md`，记录一次 FileChange，执行或申请执行 `java -version`，并生成 Markdown 格式的 TaskReport。

### 工作流模式

内置工作流：

```text
coding-agent：默认编码工作流，按节点执行任务理解、workspace 检查、项目记忆、代码理解、计划、计划项执行、验证、报告和结束。
review-agent：只读检查 workspace、执行代码理解并生成报告，不创建 FileChange。
test-agent：执行验证命令、记录 ValidationResult、生成报告并结束。
```

通过 CLI 指定工作流：

```powershell
agent run "review this project" --workspace $workspaceId --workflow review-agent
agent run "run tests" --workspace $workspaceId --workflow test-agent
```

查看工作流：

```powershell
agent workflows
agent workflow <workflowId>
agent workflow-status <runId>
agent workflow-path <runId>
```

### 项目记忆与代码理解

触发扫描并查看项目画像：

```powershell
agent scan <workspaceId>
agent profile <workspaceId>
```

查询代码符号、单文件 outline 和检索上下文：

```powershell
agent symbols <workspaceId> --query WorkflowAgentExecutor
agent outline <workspaceId> --path src/main/java/com/nask/agent/workflow/WorkflowAgentExecutor.java
agent context <workspaceId> --query "how does approval recovery work"
```

查看和写入项目记忆：

```powershell
agent memory <workspaceId>
agent remember <workspaceId> --type COMMON_COMMAND --title "Run tests" --content "Use mvn test before submitting."
agent memory-retrievals <workspaceId>
```

工作流结束时会生成任务摘要记忆提案。提案需要审批后才会写入 `project_memory_item`：

```powershell
agent memory-proposals <workspaceId>
agent memory-approve <proposalId>
agent memory-reject <proposalId> --reason "Not reusable"
```

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

批准后后端会从等待中的 workflow 节点恢复运行；拒绝后关联任务会失败，并记录审批拒绝事件。

### 用户介入流程

当真实模型输出连续被 Runtime 拒绝、工具动作无法安全恢复，或验证失败恢复预算耗尽时，任务会进入 `WAITING_USER_INPUT`。

查看待处理用户输入请求：

```powershell
agent inputs
agent input <requestId>
```

回答并恢复任务：

```powershell
agent answer <requestId> --text "请优先读取 README.md 和 pom.xml 后重新规划"
```

取消用户输入请求会让关联任务失败：

```powershell
agent cancel-input <requestId>
```

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
GET  /api/tasks/{taskId}/failures
GET  /api/tasks/{taskId}/report

GET  /api/workflows
GET  /api/workflows/{workflowId}
GET  /api/runs/{runId}/workflow
GET  /api/runs/{runId}/workflow/nodes
GET  /api/runs/{runId}/workflow/edges

GET  /api/approvals
GET  /api/approvals/{approvalId}
POST /api/approvals/{approvalId}/approve
POST /api/approvals/{approvalId}/deny

GET  /api/user-input-requests
GET  /api/user-input-requests/{requestId}
POST /api/user-input-requests/{requestId}/answer
POST /api/user-input-requests/{requestId}/cancel

GET    /api/workspaces/{workspaceId}/command-policies
POST   /api/workspaces/{workspaceId}/command-policies
DELETE /api/command-policies/{policyId}

POST /api/workspaces/{workspaceId}/scan
GET  /api/workspaces/{workspaceId}/profile
GET  /api/workspaces/{workspaceId}/scan-runs
GET  /api/workspaces/{workspaceId}/symbols
GET  /api/workspaces/{workspaceId}/outline
GET  /api/workspaces/{workspaceId}/search-context
GET  /api/workspaces/{workspaceId}/memory
POST /api/workspaces/{workspaceId}/memory
GET  /api/workspaces/{workspaceId}/memory-retrievals
GET  /api/workspaces/{workspaceId}/memory-proposals
POST /api/memory-proposals/{proposalId}/approve
POST /api/memory-proposals/{proposalId}/reject
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
- 恢复策略选择和恢复预算。
- 阶段 1 API 集成闭环：注册 workspace、添加命令白名单、启动任务、创建 `AGENT_TASK_NOTE.md`、记录审计事件、记录文件变更并生成报告。
- 节点化工作流执行器：工具拒绝后重新规划当前计划项、验证失败后追加恢复计划、模型调用失败重试或请求用户输入、节点异常转为失败运行。
- 项目扫描和画像生成：Maven、Spring Boot、Flyway、JUnit、docs 路径识别，以及忽略目录和扫描预算。
- 文档摘要、配置摘要、任务报告摘要索引和检索排序。
- Java symbol outline、符号提取和符号搜索。
- Workflow `PROJECT_MEMORY`、`CODE_UNDERSTANDING` 和 `TASK_SUMMARY_MEMORY` 节点对项目上下文、检索记录和记忆写入提案的接入。

集成测试依赖可用的 PostgreSQL 数据源；默认读取 `application.properties` 中的连接配置。

## 设计文档

当前 `docs/` 目录只保留：

- `docs/auditable-coding-agent-plan.md`：总体建设计划。
- `docs/cli-usage-guide.md`：CLI 使用指南。

## 当前限制

- 默认 LLM 实现是 `StubLlmGateway`，固定生成三步计划；配置 `AGENT_LLM_PROVIDER=http` 后可使用真实模型，但模型输出仍受结构化解析、Bean Validation 和动作白名单约束。
- Workflow Runtime 仍是同步执行；后台任务队列、WebSocket/SSE 推送和多实例协调尚未实现。
- 阶段 4 的项目记忆和代码理解是本地确定性索引与关键词检索；尚未接入向量数据库、语言服务器、增量文件监听或语义 embedding。
- CLI 仍以本机后端 REST API 为执行入口，复杂对象的展示和交互体验还在迭代中。
- Web Dashboard 已提供基础观察面，但尚未支持实时推送、可编辑工作流 DSL、IDE 插件、多 Agent 或可视化检索调试。
- 文件修改能力已有审计和审批基础，但默认 Stub provider 目前只演示创建 `AGENT_TASK_NOTE.md`，不会自动修改业务源码。

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

短期最重要的下一步是把阶段 4 的本地项目记忆能力产品化：补充 UI 观察面、增量索引、语义检索、可视化工作流配置，并继续完善可组合工作流 DSL。
