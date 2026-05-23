# CLI 使用指南

本文档说明 `com.nask.agent.cli.AgentCli` 提供的本地命令行客户端用法。CLI 用于连接本机 Auditable Coding Agent 后端服务，支持交互式 TUI、任务提交、workspace 管理、审批处理、用户介入、工作流查询、项目扫描、代码理解和项目记忆管理。

## 1. 前置条件

CLI 依赖后端服务和项目 classpath。使用前确认已经具备：

```powershell
java -version
mvn -version
```

后端默认监听：

```text
http://localhost:8080
```

启动后端：

```powershell
mvn spring-boot:run
```

如果没有修改配置，后端会连接本地 PostgreSQL。常用环境变量如下：

```powershell
$env:DATASOURCE_URL='jdbc:postgresql://localhost:5432/auditable_agent'
$env:DATASOURCE_USERNAME='username'
$env:DATASOURCE_PASSWORD='password'
```

## 2. 构建 CLI 运行环境

CLI 入口类：

```text
com.nask.agent.cli.AgentCli
```

先编译项目并生成依赖 classpath：

```powershell
mvn -q "-DskipTests" package
mvn -q dependency:build-classpath "-Dmdep.outputFile=target/classpath.txt"
$AGENT_CP = "target\classes;$(Get-Content target\classpath.txt)"
```

可以定义一个临时 PowerShell 函数：

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

## 3. 全局选项

```powershell
agent --base-url http://localhost:8080 ...
agent --json ...
```

| 选项 | 说明 |
| --- | --- |
| `--base-url` | 覆盖本机配置中的后端服务地址。 |
| `--json` | 输出原始 JSON。未开启时，CLI 优先输出适合终端阅读的摘要。 |

不带子命令时，`agent` 会进入交互式 TUI。不带子命令但带参数时，参数会拼接为首条 prompt 并提交给 TUI。

## 4. 本地配置和会话文件

CLI 本地配置文件：

```text
%USERPROFILE%\.auditable-agent\config.toml
```

默认配置：

```toml
base_url = "http://localhost:8080"
workflow = "coding-agent"
permission_preset = "workspace-write"
model = ""
profile = "default"
```

交互式会话 transcript 写入：

```text
%USERPROFILE%\.auditable-agent\sessions\<session-id>.jsonl
```

每条 JSONL 记录包含 `timestamp`、`type`、`workspaceId`、`conversationId`、`taskId`、`runId`、`status` 和 `text` 等字段。Dashboard 的 CLI 设置和会话页面也读取这个本机目录。

## 5. 交互式 TUI

启动交互会话：

```powershell
agent
```

显式启动 TUI：

```powershell
agent tui
```

带首条 prompt 启动：

```powershell
agent "review this project and explain the risky files"
agent tui "create an audited note and validate"
```

### 5.1 Workspace 解析

首次提交任务时，TUI 会把启动 `agent` 的当前目录作为 workspace root：

- 如果该目录已经注册，直接复用对应 workspace。
- 如果该目录还未注册，会询问是否信任并注册。
- 可以使用 `/workspace <workspaceId>` 为当前终端会话临时指定 workspace。

### 5.2 Slash commands

| 命令 | 说明 |
| --- | --- |
| `/status` | 查看当前 session、base URL、权限、workspace、task 和 run 状态。 |
| `/plan` | 查看当前 run 的计划项。 |
| `/diff` | 查看当前 run 记录的文件变更。 |
| `/approvals` | 查看当前 run 的审批请求；无当前 run 时查看全局待审批请求。 |
| `/permissions` | 查看或设置权限预设。 |
| `/workspace` | 查看当前 workspace，或临时设置当前会话 workspace UUID。 |
| `/resume last` | 恢复最近一次 CLI 会话。 |
| `/new` | 在当前终端中开始新对话。 |
| `/clear` | 清屏。 |
| `/exit` | 退出。 |
| `/quit` | 退出。 |

### 5.3 权限预设

查看当前权限：

```text
/permissions
```

设置权限：

```text
/permissions read-only
/permissions workspace-write
/permissions full-auto
```

| 权限预设 | 行为 |
| --- | --- |
| `read-only` | 编码请求会转成 `review-agent`，避免写 workspace。 |
| `workspace-write` | 默认模式，允许受 `WorkspacePathGuard`、审批和审计保护的 workspace 写入。 |
| `full-auto` | 复用已配置的命令策略，但不会绕过 workspace、审批和审计保护。 |

### 5.4 阻塞状态处理

TUI 会轮询当前任务直到完成、失败或进入阻塞状态。

当 run 进入 `WAITING_APPROVAL`：

1. TUI 打印审批请求。
2. 询问是否批准。
3. 批准后后端恢复运行；拒绝后任务进入失败路径。

当 run 进入 `WAITING_USER_INPUT`：

1. TUI 打印运行时问题。
2. 如果后端提供建议选项，TUI 支持上下键或数字选择。
3. 用户也可以选择手动输入答案。
4. 回答后后端恢复运行。

## 6. Workspace 命令

注册 trusted workspace：

```powershell
agent workspace add "D:\tmp\agent-demo"
```

列出 workspace：

```powershell
agent workspace list
```

`workspace add` 会向后端发送：

```json
{
  "rootPath": "D:\\tmp\\agent-demo",
  "trusted": true
}
```

所有文件操作和命令执行都应被限制在 trusted workspace 内。

## 7. 任务提交

基本格式：

```powershell
agent run "<task>" --workspace <workspaceId>
```

示例：

```powershell
agent run "create an audited note and validate" --workspace $workspaceId
```

指定工作流：

```powershell
agent run "review this project" --workspace $workspaceId --workflow review-agent
agent run "run tests" --workspace $workspaceId --workflow test-agent
```

内置工作流：

| 工作流 | 说明 |
| --- | --- |
| `coding-agent` | 默认编码工作流，按节点执行任务理解、workspace 检查、项目记忆、代码理解、计划、计划项执行、验证、报告和结束。 |
| `review-agent` | 只读审查工作流，检查 workspace、执行代码理解并生成报告，不创建文件变更。 |
| `test-agent` | 验证工作流，执行验证命令、记录 `ValidationResult`、生成报告并结束。 |

`run` 命令会先创建 task，再调用 start 接口启动运行。

## 8. 状态、事件、变更和报告

查看任务状态：

```powershell
agent status <taskId>
```

查看审计事件：

```powershell
agent events <taskId>
```

默认输出事件类型计数摘要；完整审计事件在 Web UI 中查看，或使用 `--json` 输出。

查看运行失败记录：

```powershell
agent failures <taskId>
```

查看文件变更：

```powershell
agent diff <taskId>
```

默认只输出文件、变更类型和增删行摘要，不打印完整 diff。

查看任务报告：

```powershell
agent report <taskId>
```

默认只输出报告的核心结论，不打印 workflow、audit trail、recovery records 和原始验证日志。完整报告在 Web UI 中查看，或使用：

```powershell
agent --json report <taskId>
```

排查任务时建议顺序：

```powershell
agent status <taskId>
agent events <taskId>
agent failures <taskId>
agent diff <taskId>
agent report <taskId>
```

如果任务卡住，优先查看：

```powershell
agent approvals
agent inputs
```

## 9. Workflow 查询

列出工作流定义：

```powershell
agent workflows
```

查看单个工作流定义：

```powershell
agent workflow <workflowId>
```

查看某次 run 使用的工作流：

```powershell
agent workflow-status <runId>
```

查看某次 run 的节点和边执行历史：

```powershell
agent workflow-path <runId>
```

`workflow-path` 会输出两个接口的结果：

```text
/api/runs/{runId}/workflow/nodes
/api/runs/{runId}/workflow/edges
```

## 10. 项目扫描与代码理解

触发 bounded project scan：

```powershell
agent scan <workspaceId>
```

查看最新项目画像：

```powershell
agent profile <workspaceId>
```

搜索代码符号：

```powershell
agent symbols <workspaceId> --query WorkflowAgentExecutor
```

按符号类型过滤：

```powershell
agent symbols <workspaceId> --query Agent --type CLASS
```

查看单个 workspace-relative 文件的 outline：

```powershell
agent outline <workspaceId> --path src/main/java/com/nask/agent/workflow/WorkflowAgentExecutor.java
```

检索统一项目上下文：

```powershell
agent context <workspaceId> --query "how does approval recovery work"
```

可选过滤参数：

```powershell
agent context <workspaceId> `
  --query "approval recovery" `
  --memory-type COMMON_COMMAND `
  --document-type README `
  --symbol-type CLASS `
  --limit 20
```

## 11. 项目记忆

查看项目记忆：

```powershell
agent memory <workspaceId>
```

手动写入已批准记忆：

```powershell
agent remember <workspaceId> `
  --type COMMON_COMMAND `
  --title "Run tests" `
  --content "Use mvn test before submitting."
```

可选参数：

```text
--scope workspace
--status APPROVED
--confidence 1.0
--source-path README.md
```

查看记忆检索记录：

```powershell
agent memory-retrievals <workspaceId>
```

查看记忆写入提案：

```powershell
agent memory-proposals <workspaceId>
```

批准提案：

```powershell
agent memory-approve <proposalId>
```

拒绝提案：

```powershell
agent memory-reject <proposalId> --reason "Not reusable"
```

工作流结束时可生成任务摘要类记忆提案。提案必须批准后才会成为可检索的项目记忆。

## 12. 审批流程

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

批准请求会发送：

```json
{
  "resolvedBy": "cli"
}
```

拒绝请求会发送：

```json
{
  "resolvedBy": "cli",
  "reason": "Denied from CLI"
}
```

批准后，后端会从等待中的 workflow 节点恢复运行。拒绝后，关联任务会进入失败路径并记录审计事件。

## 13. 用户介入流程

当真实模型输出连续被 Runtime 拒绝、工具动作无法安全恢复，或验证失败恢复预算耗尽时，任务可能进入 `WAITING_USER_INPUT`。

查看待处理用户输入请求：

```powershell
agent inputs
```

查看单个请求：

```powershell
agent input <requestId>
```

回答并恢复任务：

```powershell
agent answer <requestId> --text "请优先读取 README.md 和 pom.xml 后重新规划"
```

取消用户输入请求并让关联任务失败：

```powershell
agent cancel-input <requestId>
```

## 14. 命令策略

命令执行受 workspace 级策略控制。添加 allowlist：

```powershell
agent command allow --workspace <workspaceId> --exec java --args "-version"
```

`--args` 使用逗号分隔：

```powershell
agent command allow --workspace <workspaceId> --exec mvn --args "test,-q"
```

查看 workspace 命令策略：

```powershell
agent command list --workspace <workspaceId>
```

当前 CLI 暴露了新增 allowlist 和 list。删除命令策略需要使用 REST API：

```text
DELETE /api/command-policies/{policyId}
```

## 15. 完整闭环示例

准备测试 workspace：

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

模型通常会建议执行 `java -version`、`mvn test` 等验证命令。为了让任务直接完成，可以先加入对应命令白名单：

```powershell
agent command allow --workspace $workspaceId --exec java --args "-version"
```

提交并启动任务：

```powershell
$runJson = agent run "create an audited note and validate" --workspace $workspaceId
$run = $runJson | ConvertFrom-Json
$runId = $run.id
$taskId = $run.taskId
```

查看状态、工作流、文件变更和报告：

```powershell
agent status $taskId
agent events $taskId
agent workflow-status $runId
agent workflow-path $runId
agent diff $taskId
agent report $taskId
```

Agent 会按真实模型返回的结构化计划执行受控文件操作和命令操作，记录 `FileChange`，执行或申请执行验证命令，并生成 Markdown 格式的 `TaskReport`。

## 16. 输出模式建议

人工查看时使用默认摘要格式：

```powershell
agent status <taskId>
```

脚本处理时使用原始 JSON：

```powershell
agent --json workspace list
agent --json run "run tests" --workspace $workspaceId --workflow test-agent
```

PowerShell 中可以直接转换：

```powershell
$result = agent --json workspace list | ConvertFrom-Json
```

## 17. 常见问题排查

### 后端连接失败

确认服务已启动：

```powershell
mvn spring-boot:run
```

如果服务地址不是默认值，使用：

```powershell
agent --base-url http://localhost:8081 status <taskId>
```

或修改：

```text
%USERPROFILE%\.auditable-agent\config.toml
```

### 任务一直等待

先看任务状态：

```powershell
agent status <taskId>
```

再看是否等待审批或用户输入：

```powershell
agent approvals
agent inputs
```

### 命令执行被拒绝

查看当前 workspace 的命令策略：

```powershell
agent command list --workspace <workspaceId>
```

如果确认命令安全，添加 allowlist：

```powershell
agent command allow --workspace <workspaceId> --exec <executable> --args "<arg1>,<arg2>"
```

### TUI 选错 workspace

在 TUI 中查看当前 workspace：

```text
/workspace
```

为当前终端会话临时指定 workspace：

```text
/workspace <workspaceId>
```

### 需要恢复上一次会话

在 TUI 中使用：

```text
/resume last
```

也可以查看本地 transcript：

```text
%USERPROFILE%\.auditable-agent\sessions
```
