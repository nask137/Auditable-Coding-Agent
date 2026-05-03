# 阶段 3 工作计划：状态机与工作流内核

## 1. 进度确认结论

当前项目已经具备进入阶段 3 的基础。

阶段 1 的单 Agent 执行闭环已经落地：

```text
Workspace / Task / AgentRun
AgentStep / AgentAction
Plan / PlanItem
基础文件工具
Git 只读工具
命令策略和审批
AuditEvent
ValidationResult
TaskReport
REST API
轻量 CLI
Stub LLM 和 HTTP LLM Gateway
结构化模型输出校验
```

阶段 2 的可审计运行时基础也已经落地：

```text
RuntimeFailure
FailureClassifier
RecoveryPolicy
恢复预算配置
模型输出失败重试
工具动作拒绝后的重新规划
验证失败后的恢复计划
UserInputRequest
WAITING_USER_INPUT 状态
失败和恢复事件
API / CLI 查询失败、用户输入和报告
```

当前主要限制是：

```text
DefaultAgentLoopExecutor 仍然是固定同步流程
StepType 和 ActionType 已经表达了阶段语义，但还不是可配置节点
恢复分支散落在固定 Loop 中，后续继续增强会让执行器变复杂
不同 Agent 模式还不能通过工作流配置表达
没有统一的 WorkflowDefinition、WorkflowRun、WorkflowNodeExecution 和 EdgeDecision 模型
没有 DSL 加载、校验和版本化机制
```

阶段 3 的目标不是立刻做完整 LangGraph，也不是做可视化编排，而是把当前固定 Loop 中已经验证过的执行语义抽象成最小可用的状态机内核。

## 2. 阶段目标

阶段 3 的目标是建立一个服务编码 Agent 的工作流内核，让 Runtime 可以基于结构化状态执行节点、选择边、控制循环、记录决策，并逐步替换固定 Agent Loop。

核心判断标准：

```text
同一个 Runtime 可以通过不同工作流定义运行不同 Agent 模式，
而不是把流程写死在 DefaultAgentLoopExecutor 中。
```

本阶段完成后，应能表达并运行至少三类工作流：

```text
默认 Coding Agent Workflow：理解任务、检查项目、生成计划、执行、验证、报告
只读 Review Agent Workflow：读取、搜索、分析、报告，不允许写文件或执行命令
Test Agent Workflow：检查项目、建议验证命令、执行验证、分析失败、报告
```

## 3. 阶段边界

### 3.1 本阶段要做

```text
WorkflowDefinition 领域模型
WorkflowNode 和 WorkflowEdge 模型
AgentState 聚合状态模型
节点执行接口和节点注册表
边选择和条件判断机制
工作流执行引擎
循环和预算控制
默认 Coding Agent Workflow 定义
只读 Review Workflow 定义
Test Workflow 定义
Workflow DSL 草案和加载校验
工作流执行审计事件
REST / CLI 查询工作流定义和执行状态
阶段 3 单元测试和集成测试
```

### 3.2 本阶段暂不做

```text
Web 控制台
拖拽式可视化编排
SSE / WebSocket 实时推送
异步任务队列
多实例执行锁
多 Agent 调度
插件市场
长期 Memory / RAG
复杂表达式语言
动态热更新生产工作流
工作流图布局算法
```

说明：阶段 3 先让工作流可配置、可审计、可测试。可视化展示和拖拽编辑留到阶段 5。

## 4. 设计原则

### 4.1 状态机服务编码 Agent，不做通用图引擎

第一版只支持编码 Agent 已经需要的节点和边。

节点优先覆盖当前固定 Loop：

```text
TaskUnderstandingNode
WorkspaceInspectionNode
PlanCreationNode
PlanItemExecutionNode
ValidationNode
ReportNode
ApprovalWaitNode
UserInputWaitNode
FinishNode
FailNode
```

通用节点类型可以抽象为：

```text
LLMNode
ToolNode
ConditionNode
ApprovalNode
ValidationNode
LoopNode
ReportNode
FinishNode
FailNode
```

但实现上应先使用明确的编码节点，避免过早抽象成难以调试的通用图系统。

### 4.2 Runtime 事实仍然写入现有表

阶段 3 不重写阶段 1/2 的事实源。

现有表继续作为核心事实：

```text
task
agent_run
agent_step
agent_action
plan
plan_item
tool_call
tool_result
file_change
command_execution
approval_request
runtime_failure
user_input_request
audit_event
validation_result
task_report
```

新增工作流表只记录工作流定义、节点执行和边选择，不替代已有审计事件。

### 4.3 每次节点和边决策都必须可审计

工作流内核至少要记录：

```text
进入了哪个节点
节点输入摘要是什么
节点输出摘要是什么
节点成功、失败、等待审批还是等待用户输入
选择了哪条边
选择原因是什么
条件判断输入是什么
循环和重试预算剩余多少
```

这些事实写入 AuditEvent，同时保存在工作流执行表中方便查询。

### 4.4 迁移采用并行路径，避免一次性替换固定 Loop

建议先保留 `DefaultAgentLoopExecutor`，新增 `WorkflowAgentExecutor`。

迁移顺序：

```text
先实现工作流内核
再用内核复刻当前 Coding Agent Loop
通过测试证明行为等价
最后把 Task 启动入口切到默认工作流
保留固定 Loop 作为短期回退路径
```

## 5. 核心模型

### 5.1 WorkflowDefinition

建议字段：

```text
id
name
version
description
mode
enabled
definition jsonb
created at
updated at
```

`mode` 用于区分：

```text
CODING
REVIEW
TEST
PLANNING
DEBUG
```

### 5.2 WorkflowNode

DSL 中的节点结构：

```text
node id
node type
display name
input mapping
output mapping
retry policy
timeout seconds
permission requirement
audit config
next edges
```

第一批节点类型：

```text
TASK_UNDERSTANDING
WORKSPACE_INSPECTION
PLAN_CREATION
PLAN_ITEM_EXECUTION
VALIDATION
REPORT
WAIT_APPROVAL
WAIT_USER_INPUT
CONDITION
FINISH
FAIL
```

### 5.3 WorkflowEdge

第一批边类型：

```text
ALWAYS
ON_SUCCESS
ON_FAILURE
ON_BLOCKED
ON_WAITING_APPROVAL
ON_WAITING_USER_INPUT
ON_APPROVAL_GRANTED
ON_APPROVAL_DENIED
ON_VALIDATION_FAILED
ON_MAX_RETRY
CONDITION
```

边选择必须基于结构化状态，例如：

```text
node status
run status
plan has pending item
last tool result blocked
last validation passed
last recovery strategy
budget exhausted
```

不要让边直接依赖模型返回的自然语言。

### 5.4 AgentState

AgentState 是工作流执行时的聚合视图，不是单独替代所有表的事实源。

建议包含：

```text
task
run
workspace
workflow definition
current node
current plan
current plan item
recent messages summary
recent observations
recent tool results
recent file changes
recent command executions
recent validation results
pending approval request
pending user input request
runtime failures
recovery notes
loop counters
budget metadata
last node result
last edge decision
```

第一版可以在执行时按需从 repository 装配，不必把完整 AgentState 持久化为大 JSON。

### 5.5 NodeExecutionResult

每个节点返回统一结果：

```text
status: SUCCESS / FAILURE / BLOCKED / WAITING_APPROVAL / WAITING_USER_INPUT / FINISHED
summary
payload
failure type
recovery strategy
updated plan id
updated plan item id
next hints
```

统一结果可以让边选择器不关心具体节点内部实现。

## 6. 数据库建议

新增迁移建议命名：

```text
V3__phase3_workflow_kernel.sql
```

建议新增表：

```text
workflow_definition
workflow_node_execution
workflow_edge_decision
```

### 6.1 workflow_definition

用于保存内置和用户可配置工作流。

关键字段：

```text
id
name
version
mode
enabled
definition_json
created_at
updated_at
```

唯一约束建议：

```text
(name, version)
```

### 6.2 workflow_node_execution

用于记录一次 run 中每个节点的执行。

关键字段：

```text
id
task_id
run_id
workflow_definition_id
node_id
node_type
agent_step_id
status
input_summary
output_summary
failure_id
started_at
completed_at
metadata_json
```

### 6.3 workflow_edge_decision

用于记录每次边选择。

关键字段：

```text
id
task_id
run_id
workflow_definition_id
from_node_id
to_node_id
edge_type
condition_summary
decision_reason
selected
created_at
metadata_json
```

## 7. 工作流 DSL 草案

第一版建议使用 JSON 或 YAML 均可。考虑 Spring Boot 后端默认 JSON 处理更直接，内部持久化使用 JSON；文档中可以用 YAML 展示。

默认 Coding Agent Workflow 示例：

```yaml
name: coding-agent
version: 1
mode: CODING
start: understand_task

limits:
  maxNodes: 30
  maxLoops: 10
  maxFailures: 3

nodes:
  understand_task:
    type: TASK_UNDERSTANDING
    next:
      - type: ON_SUCCESS
        to: inspect_workspace
      - type: ON_WAITING_USER_INPUT
        to: wait_user_input
      - type: ON_FAILURE
        to: fail

  inspect_workspace:
    type: WORKSPACE_INSPECTION
    input:
      path: "."
      maxDepth: 4
    permission: READ_ONLY
    next:
      - type: ON_SUCCESS
        to: create_plan
      - type: ON_WAITING_APPROVAL
        to: wait_approval
      - type: ON_FAILURE
        to: fail

  create_plan:
    type: PLAN_CREATION
    next:
      - type: ON_SUCCESS
        to: execute_plan_item
      - type: ON_WAITING_USER_INPUT
        to: wait_user_input
      - type: ON_FAILURE
        to: fail

  execute_plan_item:
    type: PLAN_ITEM_EXECUTION
    next:
      - type: CONDITION
        condition: plan.hasPendingItems
        to: execute_plan_item
      - type: ON_WAITING_APPROVAL
        to: wait_approval
      - type: ON_WAITING_USER_INPUT
        to: wait_user_input
      - type: ON_SUCCESS
        to: validate
      - type: ON_FAILURE
        to: fail

  validate:
    type: VALIDATION
    next:
      - type: ON_SUCCESS
        to: report
      - type: ON_VALIDATION_FAILED
        to: execute_plan_item
      - type: ON_WAITING_APPROVAL
        to: wait_approval
      - type: ON_WAITING_USER_INPUT
        to: wait_user_input
      - type: ON_FAILURE
        to: fail

  wait_approval:
    type: WAIT_APPROVAL
    next:
      - type: ON_APPROVAL_GRANTED
        to: execute_plan_item
      - type: ON_APPROVAL_DENIED
        to: fail

  wait_user_input:
    type: WAIT_USER_INPUT
    next:
      - type: ON_SUCCESS
        to: execute_plan_item
      - type: ON_FAILURE
        to: fail

  report:
    type: REPORT
    next:
      - type: ON_SUCCESS
        to: finish

  finish:
    type: FINISH

  fail:
    type: FAIL
```

## 8. Milestone 拆分

### Milestone 1：工作流领域模型和 schema

目标：

```text
先把工作流定义、节点执行和边决策作为可持久化事实建立起来。
```

交付内容：

```text
新增 Domain.WorkflowMode / WorkflowNodeType / WorkflowEdgeType / WorkflowNodeStatus
新增 workflow_definition 表
新增 workflow_node_execution 表
新增 workflow_edge_decision 表
新增 WorkflowDefinition / WorkflowNodeExecution / WorkflowEdgeDecision record
新增 repository 和基础 service
```

验收标准：

```text
可以保存默认工作流定义
可以查询工作流定义
可以为 run 记录节点执行
可以为 run 记录边选择
所有记录能关联 taskId / runId
```

### Milestone 2：DSL 加载和校验

目标：

```text
让工作流定义可以从结构化 JSON/YAML 加载，并在执行前发现配置错误。
```

交付内容：

```text
WorkflowDefinitionParser
WorkflowDefinitionValidator
内置 coding-agent workflow
内置 review-agent workflow
内置 test-agent workflow
启动时 seed 内置工作流
```

校验规则：

```text
start 节点必须存在
每条边的 to 节点必须存在
除 FINISH / FAIL 外，节点必须有可达终止路径
节点类型必须受支持
权限声明必须合法
循环节点必须受 limits 约束
同一 workflow 的 node id 不可重复
```

验收标准：

```text
合法 DSL 可以被加载并保存
缺失 start、悬空 edge、未知 node type 会被拒绝
默认三个工作流启动后可查询
```

### Milestone 3：AgentState 装配

目标：

```text
提供工作流执行所需的结构化状态视图。
```

交付内容：

```text
AgentState record
AgentStateAssembler
当前 task / run / workspace 装配
当前 plan / pending plan item 装配
最近 tool result / file change / command execution 装配
runtime failure 和 answered user input notes 装配
预算计数装配
```

验收标准：

```text
给定 runId 可以装配完整 AgentState
AgentState 能支撑当前 DefaultAgentLoopExecutor 中的关键判断
装配逻辑不直接修改任何运行时状态
```

### Milestone 4：节点执行接口和节点注册表

目标：

```text
把当前固定 Loop 中的阶段动作拆成可插拔节点。
```

交付内容：

```text
WorkflowNodeExecutor 接口
WorkflowNodeRegistry
NodeExecutionResult
TaskUnderstandingNodeExecutor
WorkspaceInspectionNodeExecutor
PlanCreationNodeExecutor
PlanItemExecutionNodeExecutor
ValidationNodeExecutor
ReportNodeExecutor
FinishNodeExecutor
FailNodeExecutor
```

迁移要求：

```text
优先复用现有 service
不把文件、命令、审批、恢复逻辑复制一份
LLM 调用仍通过 LlmGateway
文件和命令操作仍通过 FileToolService / CommandToolService
失败恢复仍通过 RuntimeFailureService / RecoveryPolicy
```

验收标准：

```text
每个节点可以独立单元测试
节点执行会创建或关联 AgentStep / AgentAction
节点返回统一 NodeExecutionResult
节点失败会产生 RuntimeFailure 或明确失败结果
```

### Milestone 5：边选择器和条件判断

目标：

```text
把流程跳转从 if/while 代码转为基于节点结果和 AgentState 的边选择。
```

交付内容：

```text
WorkflowEdgeSelector
ConditionEvaluator
结构化条件集合
边决策审计事件
workflow_edge_decision 持久化
```

第一批结构化条件：

```text
plan.hasPendingItems
plan.completed
lastNode.status == SUCCESS
lastNode.status == WAITING_APPROVAL
lastNode.status == WAITING_USER_INPUT
lastValidation.passed
lastRecovery.strategy == REPLAN_REMAINING_PLAN
budget.exhausted
```

验收标准：

```text
执行完节点后能稳定选择下一节点
每次选择都有 decision reason
无法选择边时任务失败并写清楚原因
条件判断不依赖自然语言解析
```

### Milestone 6：工作流执行引擎

目标：

```text
实现最小可用的 WorkflowRuntime，可以跑通默认 Coding Agent Workflow。
```

交付内容：

```text
WorkflowAgentExecutor
WorkflowRuntime
WorkflowRunContext
循环和预算控制
暂停和恢复处理
终态处理
审计事件补齐
```

执行规则：

```text
每次只执行当前节点
节点完成后根据边选择下一节点
WAITING_APPROVAL / WAITING_USER_INPUT 立即暂停
恢复后从暂停节点或配置的恢复边继续
达到 maxNodes / maxLoops / maxFailures 后失败
FINISH 节点完成 run 和 task
FAIL 节点失败 run 和 task 并生成报告
```

验收标准：

```text
默认 Coding Agent Workflow 能跑通当前 Stub 场景
审批暂停后批准可以恢复
用户输入回答后可以恢复
验证失败恢复仍受预算控制
每个节点和边都有可查询记录
```

### Milestone 7：多工作流模式

目标：

```text
证明同一个 Runtime 可以运行不同 Agent 模式。
```

交付内容：

```text
Review Agent Workflow
Test Agent Workflow
Task 创建或启动时选择 workflow
WorkflowMode 权限约束
只读模式下禁止 WORKSPACE_WRITE 和 SHELL_RISKY
```

验收标准：

```text
Review Workflow 可以读取、搜索、生成报告，不创建 FileChange
Test Workflow 可以建议并执行验证命令，记录 ValidationResult
Coding Workflow 保持原有创建文件、审批、验证和报告能力
不同工作流通过配置区分，不需要新增一套硬编码 executor
```

### Milestone 8：API / CLI 观察能力

目标：

```text
用户可以查看工作流定义、当前节点和执行路径。
```

建议 API：

```text
GET /api/workflows
GET /api/workflows/{workflowId}
GET /api/runs/{runId}/workflow
GET /api/runs/{runId}/workflow/nodes
GET /api/runs/{runId}/workflow/edges
```

建议 CLI：

```text
agent workflows
agent workflow <workflowId>
agent run "<task>" --workspace <workspaceId> --workflow coding-agent
agent workflow-status <runId>
agent workflow-path <runId>
```

验收标准：

```text
可以看到 run 使用了哪个 workflow
可以看到当前节点和历史节点
可以看到每次边选择原因
可以区分任务暂停在审批、用户输入还是失败节点
```

### Milestone 9：报告和文档

目标：

```text
让阶段 3 能被手工验证和后续阶段复用。
```

交付内容：

```text
TaskReport 增加 Workflow section
README 更新阶段 3 能力和使用方式
docs/step3/phase3-cli-test-guide.md
docs/step3/default-workflows.md
```

验收标准：

```text
报告中包含 workflow name/version、节点路径和关键边决策
文档能指导用户跑通 coding/review/test 三个 workflow
```

## 9. 建议实施顺序

```text
1. 新增 workflow schema、领域模型和 repository
2. 实现 DSL parser / validator，并 seed 内置工作流
3. 实现 AgentStateAssembler
4. 抽出 NodeExecutionResult 和 WorkflowNodeExecutor
5. 逐步迁移当前固定 Loop 的理解、检查、计划、执行、验证、报告节点
6. 实现 EdgeSelector 和结构化条件判断
7. 实现 WorkflowAgentExecutor，先只跑 coding-agent
8. 补齐审批暂停、用户输入暂停和恢复路径
9. 增加 review-agent 和 test-agent 工作流
10. 增加 API / CLI 查询能力
11. 扩展报告、README 和阶段 3 手工测试文档
12. 评估是否把默认 Task 启动入口切到 WorkflowAgentExecutor
```

推荐不要第一步就删除 `DefaultAgentLoopExecutor`。等工作流执行器覆盖当前端到端测试后，再切换默认路径。

## 10. 测试计划

### 10.1 单元测试

重点覆盖：

```text
WorkflowDefinitionValidator
WorkflowDefinitionParser
AgentStateAssembler
WorkflowEdgeSelector
ConditionEvaluator
各 NodeExecutor
Workflow limits 和循环预算
WorkflowMode 权限约束
```

典型测试用例：

```text
缺失 start 节点会被拒绝
edge 指向不存在节点会被拒绝
FINISH / FAIL 节点可以没有 next
plan.hasPendingItems 为 true 时回到 execute_plan_item
validation failed 且 recovery plan appended 时回到 execute_plan_item
WAITING_APPROVAL 时暂停，不继续选择普通 success 边
WAITING_USER_INPUT 时暂停，不继续执行后续节点
只读 workflow 拒绝 WORKSPACE_WRITE 节点
```

### 10.2 集成测试

重点覆盖：

```text
默认 Coding Workflow 使用 Stub LLM 跑通完整任务
Coding Workflow 触发命令审批后暂停，批准后恢复
模型输出失败触发 RuntimeFailure，并通过工作流继续
验证失败追加 recovery plan，并通过边回到 execute_plan_item
用户输入请求创建后暂停，回答后恢复
Review Workflow 不产生 FileChange
Test Workflow 产生 ValidationResult 和报告
workflow_node_execution 和 workflow_edge_decision 可查询
```

### 10.3 手工 CLI 测试

准备场景：

```text
使用 stub provider 跑 coding-agent
不加入 java -version 白名单，验证审批暂停
加入白名单后验证任务完成
使用 review-agent 执行只读分析
使用 test-agent 执行验证命令
使用 http provider 故意制造模型输出非法或不支持工具动作
```

检查：

```text
agent status
agent events
agent failures
agent inputs
agent workflow-status
agent workflow-path
agent report
```

## 11. 阶段 3 验收标准

阶段 3 完成后，应满足：

```text
工作流定义可以持久化、查询和校验
默认 Coding Agent Workflow 可以通过工作流内核跑通当前 MVP 场景
至少 Review 和 Test 两种额外 Agent 模式可以通过不同工作流运行
节点执行和边选择都有结构化记录
工作流执行可以在审批和用户输入处暂停并恢复
循环、重试和失败恢复仍受预算限制
RuntimeFailure / UserInputRequest / ApprovalRequest 继续与工作流执行关联
报告包含 workflow 路径摘要
API / CLI 可以查看 workflow 定义、节点历史和边选择原因
DefaultAgentLoopExecutor 可以保留为回退，但默认路径可以切换到 WorkflowAgentExecutor
```

## 12. 风险和约束

主要风险：

```text
过早设计通用 DSL，会拖慢当前编码 Agent 的实际可用性
节点抽象如果太薄，只是把 if/else 换个地方，无法降低复杂度
节点抽象如果太厚，会复制现有 service 逻辑，造成两套 Runtime
边条件如果支持任意脚本，会引入安全和可测试性问题
暂停恢复如果没有明确当前节点语义，容易重复执行文件或命令动作
```

约束建议：

```text
第一版只支持白名单结构化条件
节点执行必须幂等或能识别已完成事实
所有文件和命令操作继续走现有工具服务
所有失败恢复继续走 RuntimeFailureService 和 RecoveryPolicy
工作流定义先以内置模板为主，用户自定义只做持久化和校验，不做复杂编辑器
```

## 13. 完成后的下一阶段入口

阶段 3 完成后，进入阶段 4 的条件是：

```text
工作流内核能稳定运行 coding/review/test 模式
AgentState 已经成为统一的运行时上下文入口
节点和边的执行路径可查询、可审计
不同工作流可以声明权限和运行限制
任务报告能还原工作流路径
```

具备这些条件后，阶段 4 的项目记忆和代码理解可以作为新的节点能力接入，例如：

```text
ProjectScanNode
MemoryRetrieveNode
CodeOutlineNode
SymbolSearchNode
TaskSummaryMemoryNode
```

这样 Memory 不需要侵入固定 Loop，而是成为工作流内核中的可组合能力。
