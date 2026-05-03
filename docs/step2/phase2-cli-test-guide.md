# 阶段 2 CLI 测试指南

## 1. 文档目标

本文档描述阶段 2 如何通过 CLI 验收可审计运行时能力。

阶段 2 的测试重点不是验证 Agent 是否更聪明，而是确认 Runtime 在失败、拒绝和恢复路径上具备可观察、可审计、可恢复的行为：

```text
可以查询 RuntimeFailure
可以看到恢复相关 AuditEvent
可以在需要用户介入时进入 WAITING_USER_INPUT
可以通过 CLI 回答用户介入请求并恢复任务
可以取消用户介入请求并让任务失败
最终报告包含 Failure and Recovery 摘要
```

默认 `StubLlmGateway` 行为稳定，不会自然产生非法模型输出。因此本文档分为两类验收：

```text
Stub 基线验收：验证阶段 2 CLI、API 和报告入口可用，正常闭环不回归。
HTTP / Mock 模型恢复验收：验证真实模型输出被 Runtime 拒绝后的失败恢复和用户介入。
```

## 2. 前置条件

### 2.1 Java、Maven 和 PostgreSQL

建议使用项目当前 Java 25 环境：

```powershell
java -version
mvn -version
```

准备 PostgreSQL。README 默认示例为：

```powershell
docker run --name agent-postgres `
  -e POSTGRES_DB=auditable_agent `
  -e POSTGRES_USER=username `
  -e POSTGRES_PASSWORD=password `
  -p 5432:5432 `
  -d postgres:16
```

配置环境变量：

```powershell
$env:DATASOURCE_URL='jdbc:postgresql://localhost:5432/auditable_agent'
$env:DATASOURCE_USERNAME='username'
$env:DATASOURCE_PASSWORD='password'
```

阶段 2 启动时 Flyway 应执行：

```text
V1__phase1_core_schema.sql
V2__phase2_runtime_recovery.sql
```

### 2.2 自动测试基线

CLI 手工测试前，建议先跑非数据库单元测试：

```powershell
mvn test -Dtest=!Phase1ApiIntegrationTests
```

如果本地 PostgreSQL 已正确配置，再跑完整测试：

```powershell
mvn test
```

## 3. 启动服务和 CLI

### 3.1 启动 Stub Provider 服务

第一个 PowerShell 窗口：

```powershell
$env:AGENT_LLM_PROVIDER='stub'
$env:DATASOURCE_URL='jdbc:postgresql://localhost:5432/auditable_agent'
$env:DATASOURCE_USERNAME='username'
$env:DATASOURCE_PASSWORD='password'

mvn spring-boot:run
```

服务默认监听：

```text
http://localhost:8080
```

### 3.2 准备 CLI

第二个 PowerShell 窗口：

```powershell
mvn -q "-DskipTests" package
mvn -q dependency:build-classpath "-Dmdep.outputFile=target/classpath.txt"
$AGENT_CP = "target\classes;$(Get-Content target\classpath.txt)"

function agent {
  java -cp $AGENT_CP com.nask.agent.cli.AgentCli @args
}
```

确认阶段 2 命令存在：

```powershell
agent --help
```

预期输出包含：

```text
failures
inputs
input
answer
cancel-input
```

## 4. 用例 1：Stub 正常闭环不回归

目标：

```text
验证阶段 2 改造后，阶段 1 的正常执行闭环仍然可用。
```

### 4.1 准备 workspace

```powershell
$workspacePath = "D:\tmp\agent-step2-cli-happy"
Remove-Item -Recurse -Force $workspacePath -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $workspacePath | Out-Null
Set-Content "$workspacePath\README.md" "step2 cli happy path"
```

### 4.2 注册 workspace 并配置验证命令白名单

```powershell
$workspaceJson = agent workspace add $workspacePath
$workspace = $workspaceJson | ConvertFrom-Json
$workspaceId = $workspace.id

agent command allow --workspace $workspaceId --exec java --args "-version"
agent command list --workspace $workspaceId
```

### 4.3 启动任务

```powershell
$runJson = agent run "create an audited note and validate step2 runtime" --workspace $workspaceId
$run = $runJson | ConvertFrom-Json
$taskId = $run.taskId
$runId = $run.id

$run.status
```

预期结果：

```text
run.status 为 COMPLETED
workspace 内生成 AGENT_TASK_NOTE.md
```

### 4.4 查询阶段 2 观察入口

```powershell
agent status $taskId
agent events $taskId
agent failures $taskId
agent inputs
agent report $taskId
```

预期结果：

```text
failures 返回空数组 []
inputs 返回空数组 []，或不包含当前 run 的 PENDING 请求
events 包含 AgentRunStarted、PlanCreated、ValidationCompleted、AgentFinished
report 包含 Failure and Recovery 标题
```

## 5. 用例 2：审批暂停路径仍然可用

目标：

```text
确认阶段 2 的 WAITING_USER_INPUT 没有破坏阶段 1 的 WAITING_APPROVAL。
```

不要给本 workspace 添加 `java -version` 白名单。

```powershell
$approvalPath = "D:\tmp\agent-step2-cli-approval"
Remove-Item -Recurse -Force $approvalPath -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $approvalPath | Out-Null
Set-Content "$approvalPath\README.md" "step2 cli approval path"

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
```

批准审批并恢复：

```powershell
$approvals = agent approvals | ConvertFrom-Json
$approval = @($approvals | Where-Object { $_.runId -eq $run.id })[0]
agent approve $approval.id

agent status $approvalTaskId
agent events $approvalTaskId
agent failures $approvalTaskId
```

预期结果：

```text
task.status 为 COMPLETED
events 包含 ApprovalRequested、ApprovalGranted、AgentRunResumed、CommandExecuted、AgentFinished
failures 为空数组 []
```

## 6. 用例 3：RuntimeFailure 查询入口

目标：

```text
验证 CLI 可以查询结构化失败记录。
```

在 Stub 正常路径下：

```powershell
agent failures $taskId
```

预期结果：

```text
返回 []
```

在后续 HTTP / Mock 模型恢复用例中，该命令应返回类似：

```text
failureType
recoverable
strategy
summary
attempt
createdAt
```

## 7. 用例 4：HTTP / Mock 模型输出非法后重试

目标：

```text
验证真实模型输出被 Runtime 拒绝后，Runtime 会记录失败并按预算重试。
```

本用例需要使用 HTTP-compatible Mock 模型服务。Mock 行为：

```text
第一次 create plan 或 decide next action 返回非法 JSON
第二次返回合法 JSON
```

在单独 PowerShell 窗口启动仓库内置 Mock LLM：

```powershell
python tools\mock_llm_server.py --port 9000
```

如果要重复执行某个用例，可先重置 Mock LLM 的内存计数：

```powershell
Invoke-RestMethod http://localhost:9000/reset
```

启动 Agent Service 时使用：

```powershell
$env:AGENT_LLM_PROVIDER='http'
$env:AGENT_LLM_BASE_URL='http://localhost:9000'
$env:AGENT_LLM_API_KEY='test-key'
$env:AGENT_LLM_MODEL='mock-model'
$env:AGENT_LLM_THINKING_ENABLED='false'

mvn spring-boot:run
```

提交任务：

```powershell
Invoke-RestMethod http://localhost:9000/reset

$mockPath = "D:\tmp\agent-step2-cli-model-retry"
Remove-Item -Recurse -Force $mockPath -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $mockPath | Out-Null
Set-Content "$mockPath\README.md" "step2 model retry workspace"

$workspaceJson = agent workspace add $mockPath
$workspace = $workspaceJson | ConvertFrom-Json
$mockWorkspaceId = $workspace.id

$runJson = agent run "exercise model output retry recovery" --workspace $mockWorkspaceId
$run = $runJson | ConvertFrom-Json
$mockTaskId = $run.taskId

agent failures $mockTaskId
agent events $mockTaskId
```

预期结果：

```text
failures 至少包含 MODEL_OUTPUT_PARSE_FAILED
failure.strategy 为 RETRY_SAME_ACTION
events 包含 ModelCallFailed、RuntimeRejected、RecoveryStarted、RecoveryRetried 或后续成功事件
任务最终不是因为第一次非法 JSON 直接失败
```

如果 Mock 服务连续返回非法 JSON 并超过 `agent.loop.max-model-retries`，预期任务进入：

```text
WAITING_USER_INPUT
```

## 8. 用例 5：Runtime 拒绝工具意图后重新规划

目标：

```text
验证模型提出不支持或不安全工具动作时，Runtime 会记录失败并触发重新规划。
```

Mock 模型行为：

```text
第一次 decide next action 返回 unsupported tool type，或请求越界路径
第二次 replan 返回合法 recovery plan item
后续 decide next action 返回合法工具动作
```

提交任务：

```powershell
Invoke-RestMethod http://localhost:9000/reset

$rejectPath = "D:\tmp\agent-step2-cli-runtime-reject"
Remove-Item -Recurse -Force $rejectPath -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $rejectPath | Out-Null
Set-Content "$rejectPath\README.md" "step2 runtime reject workspace"

$workspaceJson = agent workspace add $rejectPath
$workspace = $workspaceJson | ConvertFrom-Json
$rejectWorkspaceId = $workspace.id

$runJson = agent run "exercise runtime rejected tool recovery" --workspace $rejectWorkspaceId
$run = $runJson | ConvertFrom-Json
$rejectTaskId = $run.taskId

agent failures $rejectTaskId
agent events $rejectTaskId
agent report $rejectTaskId
```

预期结果：

```text
failures 包含 UNSUPPORTED_TOOL_INTENT 或 PATH_ACCESS_BLOCKED
failure.strategy 为 REPLAN_CURRENT_ITEM
events 包含 RuntimeRejected、RecoveryStarted、PlanUpdated、RecoveryReplanned
report 的 Failure and Recovery 区域包含对应失败和恢复策略
```

## 9. 用例 6：恢复预算耗尽后请求用户介入

目标：

```text
验证恢复预算耗尽时，任务进入 WAITING_USER_INPUT，并可通过 CLI 回答恢复。
```

建议启动服务时将预算调低，便于复现：

```powershell
$env:AGENT_LOOP_MAX_MODEL_RETRIES='0'
$env:AGENT_LOOP_MAX_REPLAN_ATTEMPTS='0'
$env:AGENT_LOOP_MAX_USER_INPUT_REQUESTS_PER_RUN='3'
```

Mock 模型行为：

```text
持续返回非法 JSON，或持续返回 Runtime 会拒绝的动作
```

提交任务：

```powershell
Invoke-RestMethod http://localhost:9000/reset

$inputPath = "D:\tmp\agent-step2-cli-user-input"
Remove-Item -Recurse -Force $inputPath -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $inputPath | Out-Null
Set-Content "$inputPath\README.md" "step2 user input workspace"

$workspaceJson = agent workspace add $inputPath
$workspace = $workspaceJson | ConvertFrom-Json
$inputWorkspaceId = $workspace.id

$runJson = agent run "force runtime to ask for user input" --workspace $inputWorkspaceId
$run = $runJson | ConvertFrom-Json
$inputTaskId = $run.taskId

agent status $inputTaskId
agent failures $inputTaskId
agent inputs
```

预期结果：

```text
task.status 为 WAITING_USER_INPUT
failures 包含 recoverable=true、strategy=ASK_USER
inputs 返回一个 PENDING 请求
events 包含 UserInputRequested、RecoveryUserInputRequested、AgentRunPaused
```

查看请求并回答：

```powershell
$inputs = agent inputs | ConvertFrom-Json
$request = @($inputs | Where-Object { $_.runId -eq $run.id })[0]

agent input $request.id
agent answer $request.id --text "请放弃被拒绝动作，先读取 README.md 后重新规划"

agent status $inputTaskId
agent events $inputTaskId
```

预期结果：

```text
answer 返回 status=ANSWERED
events 包含 UserInputProvided、AgentRunResumed
任务恢复 RUNNING 后继续执行，最终 COMPLETED 或进入新的可审计失败/介入状态
```

## 10. 用例 7：取消用户介入请求

目标：

```text
验证用户取消介入请求时，任务失败且有审计事件。
```

可以复用用例 6 的 WAITING_USER_INPUT 场景，重新创建一个任务后执行：

```powershell
$inputs = agent inputs | ConvertFrom-Json
$request = @($inputs | Where-Object { $_.status -eq "PENDING" })[0]

agent cancel-input $request.id
agent status $request.taskId
agent events $request.taskId
```

预期结果：

```text
input request status 为 CANCELLED
task.status 为 FAILED
events 包含 UserInputCancelled、AgentFailed
```

## 11. 验收清单

完成阶段 2 CLI 验收后，应确认：

```text
agent --help 展示 failures、inputs、input、answer、cancel-input
正常 Stub 任务仍可 COMPLETED
正常路径 failures 为空
报告包含 Failure and Recovery 区域
审批 approve / deny 流程未回归
模型输出非法会记录 RuntimeFailure
Runtime 拒绝工具动作会记录 RuntimeFailure
可恢复失败会记录恢复策略和尝试次数
重规划会产生 PlanUpdated / RecoveryReplanned 事件
预算耗尽会创建 UserInputRequest
agent inputs 可以看到 PENDING 用户介入请求
agent answer 可以恢复任务
agent cancel-input 可以失败任务
```

## 12. 当前限制

阶段 2 CLI 验收仍有以下限制：

```text
CLI 输出仍是原始 JSON，不做表格化展示
StubLlmGateway 不会自然触发模型输出非法或 Runtime 拒绝场景
真实恢复路径建议使用 HTTP-compatible Mock 模型稳定复现
当前没有 WebSocket / SSE 实时事件推送
当前没有 Web 控制台，所有观察通过 CLI / REST JSON 完成
```

这些限制符合阶段 2 的目标：优先验证 Runtime 的可审计失败恢复能力，而不是构建可视化产品体验。
