# 阶段 3 CLI 手工测试指南

## 1. 启动服务

```powershell
mvn spring-boot:run
```

准备 CLI classpath：

```powershell
mvn -q "-DskipTests" package
mvn -q dependency:build-classpath "-Dmdep.outputFile=target/classpath.txt"
$AGENT_CP = "target\classes;$(Get-Content target\classpath.txt)"
function agent {
  java -cp $AGENT_CP com.nask.agent.cli.AgentCli @args
}
```

## 2. 准备 workspace

```powershell
$workspacePath = "D:\tmp\agent-step3-demo"
New-Item -ItemType Directory -Force $workspacePath | Out-Null
Set-Content "$workspacePath\README.md" "phase 3 workflow demo"

$workspaceJson = agent workspace add $workspacePath
$workspace = $workspaceJson | ConvertFrom-Json
$workspaceId = $workspace.id
```

## 3. 查看内置工作流

```powershell
agent workflows
```

预期：

```text
返回 coding-agent、review-agent、test-agent。
```

## 4. coding-agent

加入验证命令白名单：

```powershell
agent command allow --workspace $workspaceId --exec java --args "-version"
```

运行默认工作流：

```powershell
$runJson = agent run "create an audited note and validate" --workspace $workspaceId --workflow coding-agent
$run = $runJson | ConvertFrom-Json
$runId = $run.id
$taskId = $run.taskId
```

检查：

```powershell
agent status $taskId
agent workflow-status $runId
agent workflow-path $runId
agent diff $taskId
agent report $taskId
```

预期：

```text
任务 COMPLETED。
workspace 中出现 AGENT_TASK_NOTE.md。
workflow path 中包含 understand_task、inspect_workspace、create_plan、execute_plan_item、validate、finish。
报告包含 Workflow section。
```

## 5. review-agent

```powershell
$reviewJson = agent run "review this project" --workspace $workspaceId --workflow review-agent
$review = $reviewJson | ConvertFrom-Json
agent workflow-path $review.id
agent diff $review.taskId
agent report $review.taskId
```

预期：

```text
任务 COMPLETED。
不会新增 FileChange。
workflow path 中包含 inspect_workspace、report、finish。
```

## 6. test-agent

```powershell
$testJson = agent run "run validation" --workspace $workspaceId --workflow test-agent
$test = $testJson | ConvertFrom-Json
agent workflow-path $test.id
agent report $test.taskId
```

预期：

```text
任务 COMPLETED。
记录 ValidationResult。
workflow path 中包含 validate。
```

## 7. 审批恢复

删除或不要添加 `java -version` 白名单后运行：

```powershell
$pausedJson = agent run "create an audited note and validate" --workspace $workspaceId --workflow coding-agent
$paused = $pausedJson | ConvertFrom-Json
agent approvals
```

批准：

```powershell
agent approve <approvalId>
agent workflow-path $paused.id
agent status $paused.taskId
```

预期：

```text
首次运行进入 WAITING_APPROVAL。
批准后恢复并完成。
workflow path 最终包含 validate 和 finish。
```
