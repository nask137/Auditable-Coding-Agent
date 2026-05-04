# 阶段 3 默认工作流

## coding-agent

默认编码工作流。当前实现通过 `WorkflowAgentExecutor` 作为统一入口，由 workflow runtime 解析内置节点和边，并通过 `WorkflowNodeExecutor` 执行节点。固定 `DefaultAgentLoopExecutor` 不再是默认 coding-agent 路径。

能力：

```text
理解任务
检查 workspace
读取项目记忆上下文
理解代码上下文
创建 Plan
执行 PlanItem
运行验证
处理审批和用户输入恢复
生成报告
记录 WorkflowNodeExecution
记录 WorkflowEdgeDecision
```

说明：阶段 4 开始，项目记忆和代码理解作为可组合节点接入；当前节点先从已有运行事实中装配上下文，后续可以替换为持久化项目记忆、索引和代码图谱能力。

## review-agent

只读审查工作流。

能力：

```text
列出 workspace 文件
理解代码上下文
生成只读审查报告
记录工作流节点和边
不创建 FileChange
不执行 shell 命令
```

适用场景：

```text
查看项目结构
生成初步审查记录
验证 workflow 内核可以运行非写入型 Agent 模式
```

## test-agent

验证工作流。

能力：

```text
通过 LLM Gateway 建议验证命令
通过 CommandToolService 执行受策略控制的命令
记录 ValidationResult
生成验证报告
记录工作流节点和边
```

注意：

```text
命令仍然受 CommandPolicy 控制。
未加入白名单的命令会进入 WAITING_APPROVAL。
审批批准后由 `WorkflowAgentExecutor` 从等待中的 workflow 节点恢复运行。
```

## 查询接口

```text
GET /api/workflows
GET /api/workflows/{workflowId}
GET /api/runs/{runId}/workflow
GET /api/runs/{runId}/workflow/nodes
GET /api/runs/{runId}/workflow/edges
```

## CLI

```powershell
agent workflows
agent workflow <workflowId>
agent run "review this project" --workspace <workspaceId> --workflow review-agent
agent run "run tests" --workspace <workspaceId> --workflow test-agent
agent workflow-status <runId>
agent workflow-path <runId>
```
