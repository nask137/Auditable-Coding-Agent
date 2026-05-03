# 阶段 3 默认工作流

## coding-agent

默认编码工作流。当前实现通过 `WorkflowAgentExecutor` 作为统一入口，复用已经稳定的固定 Agent Loop，并把持久化的 `AgentStep` 回填为 workflow 节点和边记录。

能力：

```text
理解任务
检查 workspace
创建 Plan
执行 PlanItem
运行验证
处理审批和用户输入恢复
生成报告
记录 WorkflowNodeExecution
记录 WorkflowEdgeDecision
```

说明：这是阶段 3 的兼容迁移路径，避免一次性替换阶段 1/2 已验证的安全闭环。后续可以继续把内部执行完全拆成独立节点执行器。

## review-agent

只读审查工作流。

能力：

```text
列出 workspace 文件
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
审批批准后由默认 WorkflowAgentExecutor 恢复运行。
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
