# 第4阶段 CLI 测试指南

本指南验证 CLI 中的项目内存、代码理解、检索和内存批准。

## 前提条件

启动 PostgreSQL 和后端，使用与之前阶段相同的环境。

```powershell
mvn spring-boot:run
```

在另一个终端中构建 CLI 类路径。

```powershell
mvn -q -DskipTests package
mvn -q dependency:build-classpath "-Dmdep.outputFile=target/classpath.txt"
$cp = "target/classes;" + (Get-Content target/classpath.txt)
```

下面的示例使用此帮助程序。

```powershell
function agent { java -cp $cp com.nask.agent.cli.AgentCli @args }
```

## 注册工作区

```powershell
$workspace = agent workspace add "D:\workspace\Auditable Coding Agent" | ConvertFrom-Json
$workspaceId = $workspace.id
```

## 扫描和分析

```powershell
agent scan $workspaceId
agent profile $workspaceId
```

预期的配置文件信号：

```text
Java
Spring Boot
Maven
Flyway
JUnit
README.md
docs/**
```

检查扫描历史记录。

```powershell
agent --base-url http://localhost:8080 profile $workspaceId
Invoke-RestMethod "http://localhost:8080/api/workspaces/$workspaceId/scan-runs"
```

## 符号和大纲

搜索已知的 Java 符号。

```powershell
agent symbols $workspaceId --query WorkflowAgentExecutor
agent symbols $workspaceId --query generate --type METHOD
```

检查一个文件大纲。

```powershell
agent outline $workspaceId --path "src/main/java/com/nask/agent/workflow/WorkflowAgentExecutor.java"
```

预期输出是包含 `path`、`symbolType`、`symbolName`、`lineStart` 和 `signature` 的原始 JSON。

## 上下文检索

使用确定性关键字检索搜索项目上下文。

```powershell
agent context $workspaceId --query "how to run tests" --limit 8
agent memory-retrievals $workspaceId
```

预期行为：

```text
search-context 返回 retrievalId、profile、results 和 sourceReferences。
memory-retrievals 显示持久的检索记录。
```

## 手动内存

创建已批准的内存项。

```powershell
agent remember $workspaceId `
  --type COMMON_COMMAND `
  --title "Run tests" `
  --content "Use mvn test before submitting backend changes." `
  --source-path "README.md"

agent memory $workspaceId
```

预期输出包含状态为 `APPROVED` 的 `COMMON_COMMAND` 内存项。

## 工作流内存提议

运行编码任务。默认工作流扫描、检索上下文、验证、创建任务报告并提议任务课程内存。

```powershell
$taskRun = agent run "create a small audited note and validate" --workspace $workspaceId | ConvertFrom-Json
$taskId = $taskRun.taskId
$runId = $taskRun.id
agent workflow-path $runId
agent report $taskId
agent memory-proposals $workspaceId
```

预期的工作流节点包括：

```text
project_scan
project_memory
code_understanding
task_summary_memory
```

批准提议。

```powershell
$proposal = (agent memory-proposals $workspaceId | ConvertFrom-Json)[0]
agent memory-approve $proposal.id
agent memory $workspaceId
```

预期输出包含 `TASK_LESSON` 内存项。

拒绝提议不应导致已完成的运行失败：

```powershell
agent memory-reject <proposalId> --reason "Not reusable"
agent status $taskId
```

## 报告检查

任务报告应包含：

```text
## Project Context
## File Changes
## Failure and Recovery
## Workflow
## Audit Events
```

"项目上下文"部分应列出项目配置文件、检索记录、源引用和在运行期间使用的内存提议。
