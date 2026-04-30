# 阶段 1 CLI 测试指南

## 1. 文档目标

本文档描述阶段 1 如何通过 CLI 验证单 Agent 执行闭环。

CLI 在阶段 1 中只作为后端 API 客户端，不承载 Agent Runtime 逻辑。测试重点是确认用户可以通过 CLI 完成以下操作：

```text
注册 trusted workspace
配置命令白名单
提交并启动任务
查看任务状态
查看审计事件
查看文件变更
处理审批
查看最终报告
```

当前阶段使用 `StubLlmGateway`，Agent 的固定行为是：

```text
1. 理解任务
2. 列出 workspace 文件
3. 创建 AGENT_TASK_NOTE.md
4. 建议执行 java -version 作为验证命令
5. 生成任务报告
```

因此 CLI 测试用例应围绕这个稳定闭环设计。

## 2. 前置条件

### 2.1 Java 和 Maven

建议使用项目当前验证过的 Java 25 环境：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\loom-ea-25-loom+1-11'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

java -version
mvn -version
```

### 2.2 PostgreSQL

后端默认连接：

```text
jdbc:postgresql://localhost:5432/agent
username: codex
password: codex
```

如果本机没有 PostgreSQL，可以使用 Docker 启动一个测试数据库：

```powershell
docker run --name agent-postgres `
  -e POSTGRES_DB=agent `
  -e POSTGRES_USER=codex `
  -e POSTGRES_PASSWORD=codex `
  -p 5432:5432 `
  -d postgres:16
```

如果容器已存在，可以启动它：

```powershell
docker start agent-postgres
```

### 2.3 自动测试基线

CLI 手工测试前，先确认自动测试通过：

```powershell
mvn -q test
```

## 3. 启动后端服务

在第一个 PowerShell 窗口中启动 Agent Service：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\loom-ea-25-loom+1-11'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

mvn spring-boot:run
```

服务默认监听：

```text
http://localhost:8080
```

如果需要连接其他数据库，可以覆盖环境变量：

```powershell
$env:AGENT_DATASOURCE_URL='jdbc:postgresql://localhost:5432/agent'
$env:AGENT_DATASOURCE_USERNAME='codex'
$env:AGENT_DATASOURCE_PASSWORD='codex'
```

## 4. 准备 CLI 启动方式

在第二个 PowerShell 窗口中编译项目并生成运行 classpath：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\loom-ea-25-loom+1-11'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

mvn -q "-DskipTests" package
mvn -q dependency:build-classpath "-Dmdep.outputFile=target/classpath.txt"
$AGENT_CP = "target\classes;$(Get-Content target\classpath.txt)"
```

建议定义一个临时 PowerShell 函数，方便后续命令更接近真实 CLI 使用方式：

```powershell
function agent {
  java -cp $AGENT_CP com.nask.agent.cli.AgentCli @args
}
```

验证 CLI 帮助输出：

```powershell
agent --help
agent workspace --help
agent command --help
```

预期结果：

```text
命令返回 0
输出包含 workspace、run、status、events、diff、report、approvals、approve、deny、command 等子命令
```

如果服务不在默认地址，可以传入 `--base-url`：

```powershell
agent --base-url http://localhost:8080 workspace list
```

## 5. 用例 1：白名单命令下的完整成功闭环

目标：

```text
验证 CLI 可以注册 workspace、配置 java -version 白名单、提交任务，并直接完成验证和报告生成。
```

### 5.1 准备 workspace

```powershell
$workspacePath = "D:\tmp\agent-cli-happy"
Remove-Item -Recurse -Force $workspacePath -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $workspacePath | Out-Null
Set-Content "$workspacePath\README.md" "phase1 cli happy path"
```

### 5.2 注册 workspace

```powershell
$workspaceJson = agent workspace add $workspacePath
$workspace = $workspaceJson | ConvertFrom-Json
$workspaceId = $workspace.id

agent workspace list
```

预期结果：

```text
workspace add 返回 workspace JSON
workspace list 中可以看到刚注册的 workspace
trusted 为 true
rootPath 指向测试目录
```

### 5.3 添加验证命令白名单

当前 Stub Agent 会建议执行：

```text
java -version
```

因此先加入 allowlist：

```powershell
agent command allow --workspace $workspaceId --exec java --args "-version"
agent command list --workspace $workspaceId
```

预期结果：

```text
command list 中存在 executable=java、argsPattern=["-version"]、policyType=ALLOWLIST
```

### 5.4 提交并启动任务

```powershell
$runJson = agent run "create an audited note and validate" --workspace $workspaceId
$run = $runJson | ConvertFrom-Json
$taskId = $run.taskId
$runId = $run.id

$run
```

预期结果：

```text
run.status 为 COMPLETED
workspace 内生成 AGENT_TASK_NOTE.md
```

检查文件：

```powershell
Get-Content "$workspacePath\AGENT_TASK_NOTE.md"
```

### 5.5 查看状态、事件、变更和报告

```powershell
agent status $taskId
agent events $taskId
agent diff $taskId
agent report $taskId
```

预期事件至少包含：

```text
TaskCreated
AgentRunStarted
TaskUnderstood
PlanCreated
StepStarted
ToolCallRequested
FileCreated
CommandRequested
CommandAllowed
CommandExecuted
ValidationStarted
ValidationCompleted
AgentFinished
```

预期文件变更：

```text
path 为 AGENT_TASK_NOTE.md
changeType 为 CREATE
patchApplyStatus 为 APPLIED
afterHash 不为空
lineAdded 大于 0
```

预期报告：

```text
包含任务目标
包含 Validation passed
包含 AGENT_TASK_NOTE.md 或文件变更摘要
```

## 6. 用例 2：白名单外命令触发审批并批准

目标：

```text
验证 CLI 可以看到待审批请求，并通过 approve 恢复任务执行。
```

本用例不要提前加入 `java -version` 白名单。

### 6.1 准备 workspace

```powershell
$approvalPath = "D:\tmp\agent-cli-approval"
Remove-Item -Recurse -Force $approvalPath -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $approvalPath | Out-Null
Set-Content "$approvalPath\README.md" "phase1 cli approval path"
```

### 6.2 注册 workspace 并启动任务

```powershell
$workspaceJson = agent workspace add $approvalPath
$workspace = $workspaceJson | ConvertFrom-Json
$approvalWorkspaceId = $workspace.id

$runJson = agent run "create an audited note and wait for command approval" --workspace $approvalWorkspaceId
$run = $runJson | ConvertFrom-Json
$approvalTaskId = $run.taskId

$run.status
```

预期结果：

```text
run.status 为 WAITING_APPROVAL
AGENT_TASK_NOTE.md 已创建
验证命令 java -version 未执行
```

### 6.3 查看并批准审批

```powershell
$approvalsJson = agent approvals
$approvals = $approvalsJson | ConvertFrom-Json
$approval = @($approvals | Where-Object { $_.runId -eq $run.id })[0]
$approvalId = $approval.id

agent approve $approvalId
```

预期结果：

```text
approve 返回审批记录
status 从 APPROVED 变为 CONSUMED 或后续查询中不再是 PENDING
任务执行被恢复
```

### 6.4 验证任务完成

```powershell
agent status $approvalTaskId
agent events $approvalTaskId
agent report $approvalTaskId
```

预期事件包含：

```text
ApprovalRequested
ApprovalGranted
CommandApprovalRequired
CommandExecuted
ValidationCompleted
AgentFinished
```

预期状态：

```text
task.status 为 COMPLETED
report 中包含 Validation passed
```

## 7. 用例 3：白名单外命令触发审批并拒绝

目标：

```text
验证 CLI deny 后任务失败，并且被拒绝动作不会继续执行。
```

### 7.1 准备 workspace

```powershell
$denyPath = "D:\tmp\agent-cli-deny"
Remove-Item -Recurse -Force $denyPath -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $denyPath | Out-Null
Set-Content "$denyPath\README.md" "phase1 cli deny path"
```

### 7.2 注册 workspace 并启动任务

```powershell
$workspaceJson = agent workspace add $denyPath
$workspace = $workspaceJson | ConvertFrom-Json
$denyWorkspaceId = $workspace.id

$runJson = agent run "create an audited note but deny validation command" --workspace $denyWorkspaceId
$run = $runJson | ConvertFrom-Json
$denyTaskId = $run.taskId

$run.status
```

预期结果：

```text
run.status 为 WAITING_APPROVAL
```

### 7.3 拒绝审批

```powershell
$approvalsJson = agent approvals
$approvals = $approvalsJson | ConvertFrom-Json
$approval = @($approvals | Where-Object { $_.runId -eq $run.id })[0]
$approvalId = $approval.id

agent deny $approvalId
```

### 7.4 验证失败状态

```powershell
agent status $denyTaskId
agent events $denyTaskId
```

预期结果：

```text
task.status 为 FAILED
事件包含 ApprovalDenied
事件包含 AgentFailed
不应出现审批后的 CommandExecuted
```

## 8. 用例 4：只读观察命令

目标：

```text
验证 CLI 可以作为审计查看入口，按 taskId 查询状态、事件、变更和报告。
```

可以复用用例 1 的 `$taskId`：

```powershell
agent status $taskId
agent events $taskId
agent diff $taskId
agent report $taskId
```

检查重点：

```text
status 输出 task 当前状态
events 输出按时间顺序排列的 AuditEvent
diff 输出 FileChange 和 diff/hash 信息
report 输出 Markdown 报告内容
```

## 9. 用例 5：服务地址参数

目标：

```text
验证 CLI 可以连接非默认 base URL。
```

如果后端启动在默认 8080：

```powershell
agent --base-url http://localhost:8080 workspace list
```

预期结果：

```text
输出 workspace 列表
```

如果后端未启动或 base URL 错误，CLI 应失败并显示连接错误。阶段 1 可以接受原始异常输出，后续再优化为友好的错误提示。

## 10. 验收清单

CLI 手工测试完成后，应能确认：

```text
可以通过 CLI 注册 trusted workspace
可以通过 CLI 查询 workspace 列表
可以通过 CLI 添加和查询命令白名单
可以通过 CLI 创建并启动任务
任务可以在命令白名单存在时直接完成
白名单外验证命令会生成审批请求
approve 后任务可以继续并完成
deny 后任务会失败
可以通过 CLI 查询任务状态
可以通过 CLI 查询审计事件
可以通过 CLI 查询文件变更和 diff/hash
可以通过 CLI 查询最终报告
```

## 11. 当前限制

阶段 1 CLI 仍有以下限制：

```text
CLI 输出原始 JSON，暂不做表格化或高亮展示
CLI 不保存本地配置文件，服务地址通过 --base-url 传入
CLI 不承载 Agent Runtime 逻辑
CLI 不提供交互式审批 UI，只提供 approve / deny 命令
当前 Agent 行为来自 StubLlmGateway，不代表真实 LLM 编码能力
```

这些限制符合阶段 1 的目标：优先验证本地 Runtime、权限审批、审计事件和报告闭环。
