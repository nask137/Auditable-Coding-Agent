# 阶段 2 工作计划：可审计运行时

## 1. 进度确认结论

可以正式进入 Step 2。

当前代码和文档已经基本完成阶段 1 的“单 Agent 执行闭环”目标：

```text
Spring Boot 后端
PostgreSQL + Flyway schema
Workspace / Task / AgentRun
AgentStep / AgentAction
Plan / PlanItem
基础文件工具
Git 只读工具
命令策略与审批
AuditEvent
ValidationResult
TaskReport
REST API
轻量 CLI
Stub LLM 和 HTTP LLM Gateway
结构化模型输出校验
```

README 最后一段所说：

```text
短期最重要的下一步是扩展失败恢复和事件流模型，
并在真实模型输出被 Runtime 拒绝时支持重新规划或请求用户介入。
```

这个判断属实。原因是阶段 1 已经有审计事件和失败记录基础，但仍偏“执行日志”；失败处理主要是阻止、暂停审批或直接失败；真实模型输出解析失败、结构校验失败、工具意图被 Runtime 拒绝、验证失败等场景尚未形成可恢复流程。阶段 2 应该把这些能力提升为 Runtime 的一等模型。

## 2. 阶段目标

阶段 2 的目标是把阶段 1 的固定闭环升级为更可靠的可审计运行时。

本阶段不追求复杂工作流 DSL、多 Agent、Web 控制台或长期记忆，而是优先做到：

```text
每个关键运行时事实都有稳定事件
每类失败都有结构化分类
可恢复失败可以重试、重新规划或请求用户输入
不可恢复失败可以给出明确原因和审计轨迹
真实模型输出被 Runtime 拒绝时不会直接让任务失败
```

阶段 2 的核心判断标准：

```text
Runtime 不只是执行 Agent 决策，还要能解释、拒绝、恢复和升级处理 Agent 决策。
```

## 3. 阶段边界

### 3.1 本阶段要做

```text
事件流模型标准化
AuditEvent 类型补齐和语义收敛
RuntimeFailure / RecoveryDecision 模型
模型输出拒绝后的恢复路径
工具动作被 Runtime 拒绝后的恢复路径
验证失败后的重新规划路径
用户介入请求模型
WAITING_USER_INPUT 状态落地
失败计数、重试预算和恢复预算
恢复过程的审计事件
API / CLI 查询恢复状态
阶段 2 集成测试
```

### 3.2 本阶段暂不做

```text
异步任务队列
WebSocket / SSE 实时推送
多实例执行锁
复杂状态机引擎
工作流 DSL
自动 rollback 文件变更
多 Agent
长期记忆和 RAG
Web 控制台
事件签名和防篡改哈希链
```

说明：事件流模型在阶段 2 先保持 PostgreSQL 查询模型，不引入实时推送。SSE / WebSocket 可以在阶段 5 可视化前实现。

## 4. 现状差距

### 4.1 已具备基础

```text
AuditEvent 表已有较完整字段
Domain.AuditEventType 已包含模型、工具、文件、命令、审批、验证和终态事件
HttpLlmGateway 已记录 ModelCallStarted / ModelCallCompleted / ModelCallFailed
StructuredLlmOutputValidator 已能拒绝不合法模型输出
DefaultAgentLoopExecutor 已能处理审批暂停和部分 blocked 结果
TaskStatus / AgentRunStatus 已包含 WAITING_USER_INPUT
应用配置已有 max-consecutive-failures
```

### 4.2 主要缺口

```text
没有 RuntimeFailure 领域对象
没有恢复策略模型
max-consecutive-failures 尚未系统化使用
模型输出被拒绝后直接抛异常并失败
工具动作被拒绝后通常直接失败
验证失败后直接失败，不会重新规划
WAITING_USER_INPUT 状态缺少 API、数据模型和恢复入口
事件类型不少，但缺少阶段 2 的事件契约和必填字段规范
PlanUpdated 事件还没有承载真正的重新规划语义
```

## 5. 核心设计

### 5.1 事件流模型

阶段 2 将 AuditEvent 作为事件流事实源继续使用，但要补齐事件契约。

建议将事件分为：

```text
Task lifecycle events
Run lifecycle events
Step lifecycle events
Plan events
Model decision events
Runtime decision events
Tool execution events
File events
Command events
Approval events
User input events
Recovery events
Terminal events
```

新增或明确事件：

```text
RuntimeRejected
RecoveryStarted
RecoveryRetried
RecoveryReplanned
RecoveryUserInputRequested
RecoverySkipped
RecoveryExhausted
UserInputRequested
UserInputProvided
UserInputCancelled
ValidationFailed
```

已有事件需要语义收敛：

```text
ModelCallFailed：模型调用、解析或结构校验失败
ToolCallFailed：工具执行异常或工具结果失败
CommandBlocked：命令策略阻止
PermissionBlocked：权限模型阻止
PlanUpdated：计划被 Runtime 或模型重新规划后更新
AgentRunPaused：等待审批或用户输入
AgentRunResumed：审批或用户输入后继续
AgentFailed：不可恢复或恢复预算耗尽
```

### 5.2 失败分类

新增 RuntimeFailure 分类：

```text
MODEL_CALL_FAILED
MODEL_OUTPUT_PARSE_FAILED
MODEL_OUTPUT_VALIDATION_FAILED
MODEL_DECISION_MISMATCH
UNSUPPORTED_TOOL_INTENT
TOOL_PERMISSION_BLOCKED
TOOL_EXECUTION_FAILED
PATCH_CONFLICT
PATH_ACCESS_BLOCKED
COMMAND_POLICY_BLOCKED
COMMAND_EXECUTION_FAILED
VALIDATION_FAILED
APPROVAL_DENIED
USER_INPUT_REQUIRED
RUNTIME_LIMIT_EXCEEDED
UNEXPECTED_RUNTIME_ERROR
```

每个失败至少记录：

```text
failure id
task id
run id
step id
plan item id
failure type
recoverable
summary
details
related event id
related tool call id
related command id
related file change id
attempt number
created at
```

阶段 2 可以先不单独建 `runtime_failure` 表，而是把失败事实写入 AuditEvent metadata；如果后续查询复杂，再迁移成独立表。建议本阶段先做独立表，因为恢复策略会频繁查询失败次数、最近失败和恢复历史。

### 5.3 恢复策略

定义 RecoveryStrategy：

```text
RETRY_SAME_ACTION
REPLAN_CURRENT_ITEM
REPLAN_REMAINING_PLAN
ASK_USER
REQUEST_APPROVAL
SKIP_PLAN_ITEM
FAIL_TASK
```

基础策略映射：

```text
MODEL_CALL_FAILED -> RETRY_SAME_ACTION，超过预算 ASK_USER 或 FAIL_TASK
MODEL_OUTPUT_PARSE_FAILED -> RETRY_SAME_ACTION，附带格式反馈
MODEL_OUTPUT_VALIDATION_FAILED -> RETRY_SAME_ACTION 或 REPLAN_CURRENT_ITEM
MODEL_DECISION_MISMATCH -> RETRY_SAME_ACTION，附带当前 planItemId
UNSUPPORTED_TOOL_INTENT -> REPLAN_CURRENT_ITEM
TOOL_PERMISSION_BLOCKED -> ASK_USER 或 FAIL_TASK
PATCH_CONFLICT -> REPLAN_CURRENT_ITEM，先重新读取文件
COMMAND_POLICY_BLOCKED -> ASK_USER
COMMAND_EXECUTION_FAILED -> REPLAN_CURRENT_ITEM 或 ASK_USER
VALIDATION_FAILED -> REPLAN_REMAINING_PLAN
APPROVAL_DENIED -> FAIL_TASK，后续可支持 ASK_USER
RUNTIME_LIMIT_EXCEEDED -> FAIL_TASK
```

### 5.4 用户介入模型

新增 UserInputRequest，用于区别 ApprovalRequest。

ApprovalRequest 表示：

```text
Runtime 知道下一步具体动作，但该动作需要用户批准。
```

UserInputRequest 表示：

```text
Runtime 或 Agent 无法可靠决定下一步，需要用户补充信息或选择恢复方向。
```

建议字段：

```text
id
task id
run id
step id
plan item id
status: PENDING / ANSWERED / CANCELLED / EXPIRED
question
context summary
suggested options jsonb
answer text
created at
answered at
```

建议 API：

```text
GET  /api/user-input-requests
GET  /api/user-input-requests/{requestId}
POST /api/user-input-requests/{requestId}/answer
POST /api/user-input-requests/{requestId}/cancel
```

CLI：

```text
agent inputs
agent input <requestId>
agent answer <requestId> --text "<answer>"
agent cancel-input <requestId>
```

## 6. Milestone 拆分

### Milestone 1：事件契约和失败模型

目标：

```text
把阶段 2 运行时事件和失败类型先定义清楚。
```

交付内容：

```text
补充 Domain.AuditEventType
新增 RuntimeFailureType
新增 RecoveryStrategy
新增 RuntimeFailure 数据模型和 repository
新增 FailureClassifier
新增 RecoveryPolicy
```

验收标准：

```text
模型解析失败、模型结构校验失败、工具阻止、命令失败、验证失败都能被分类
每次失败都有结构化记录
失败事件包含 recoverable、strategy、attempt、budgetRemaining
```

### Milestone 2：模型输出拒绝后的重试

目标：

```text
真实模型输出被 Runtime 拒绝时，不直接让任务失败。
```

交付内容：

```text
HttpLlmGateway 或上层 Runtime 保留拒绝原因
DefaultAgentLoopExecutor 捕获 LlmGatewayException
模型调用支持有限重试
重试 prompt 携带上次拒绝原因和期望 schema 摘要
记录 RuntimeRejected / RecoveryRetried 事件
```

验收标准：

```text
第一次返回非法 JSON，第二次返回合法 JSON，任务继续
第一次返回 unsupported tool intent，Runtime 要求重新决策，任务继续
超过重试预算后进入 WAITING_USER_INPUT 或 FAILED
```

### Milestone 3：工具动作被拒绝后的重新规划

目标：

```text
当模型提出的工具动作被 Runtime 阻止时，可以让 Agent 调整当前计划项。
```

交付内容：

```text
新增 replanCurrentItem LLM 提示
ExecutionContext 携带失败历史和 Runtime 拒绝原因
PlanService 支持替换或追加当前计划项
PlanUpdated 事件记录 old item / new item / reason
记录 RecoveryReplanned 事件
```

验收标准：

```text
模型提出越界读文件，Runtime 阻止
Runtime 记录 PATH_ACCESS_BLOCKED
Agent 重新规划为读取 workspace 内相关文件
任务不直接失败
```

### Milestone 4：验证失败后的重新规划

目标：

```text
验证命令失败时，Agent 可以基于输出继续修复，而不是立即失败。
```

交付内容：

```text
ValidationResult 失败后分类为 VALIDATION_FAILED
新增 validation failure analysis prompt
PlanService 支持追加修复计划项
重新进入 ExecutePlanItem
连续验证失败受 max-consecutive-failures 限制
```

验收标准：

```text
验证命令 exitCode 非 0
Runtime 记录 ValidationFailed
Agent 生成一个后续修复 PlanItem
再次执行并重新验证
超过失败预算后请求用户介入或失败
```

### Milestone 5：用户介入恢复

目标：

```text
Runtime 可以暂停任务等待用户补充信息，并在用户回答后继续。
```

交付内容：

```text
UserInputRequest schema
UserInputRequestService / Repository
REST API
CLI 命令
WAITING_USER_INPUT 状态流转
AgentRunPaused / AgentRunResumed 事件
UserInputRequested / UserInputProvided 事件
```

验收标准：

```text
模型连续输出无效或命令被策略阻止时，任务进入 WAITING_USER_INPUT
用户通过 API / CLI 提交回答
任务恢复并把用户回答放入下一次模型上下文
```

### Milestone 6：恢复预算和运行时限制

目标：

```text
恢复能力必须可控，不能形成无限循环。
```

交付内容：

```text
实现 max-consecutive-failures
新增 max-model-retries
新增 max-replan-attempts
新增 max-user-input-requests-per-run
恢复预算进入 AgentSettings
每次恢复记录剩余预算
预算耗尽时写 RecoveryExhausted 和 AgentFailed
```

验收标准：

```text
连续模型输出失败不会无限重试
连续验证失败不会无限修复
连续重新规划不会无限追加 PlanItem
最终失败原因清晰可查
```

### Milestone 7：API / CLI 观察能力

目标：

```text
用户可以看到失败、恢复和用户介入的完整路径。
```

交付内容：

```text
GET /api/tasks/{taskId}/failures
GET /api/runs/{runId}/failures
GET /api/tasks/{taskId}/recovery-events 或复用 events
CLI: agent failures <taskId>
CLI events 输出包含恢复事件
报告中增加 Failure and Recovery section
```

验收标准：

```text
用户可以按 taskId 查询失败列表
用户可以看到每次失败使用了什么恢复策略
最终报告包含恢复摘要
```

## 7. 建议实施顺序

```text
1. 补齐事件枚举、失败类型和恢复策略枚举
2. 增加 runtime_failure 表和服务
3. 封装 FailureClassifier 和 RecoveryPolicy
4. 改造模型调用失败处理，先支持模型输出拒绝后的重试
5. 改造 executePlanItem，支持工具拒绝后的重新规划
6. 改造 validateIfNeeded，支持验证失败后的重新规划
7. 增加 UserInputRequest 和 WAITING_USER_INPUT 恢复入口
8. 增加 API / CLI 查询能力
9. 扩展报告和测试
```

这个顺序优先处理 README 所说的短期关键问题：真实模型输出被 Runtime 拒绝后的恢复。

## 8. 测试计划

### 8.1 单元测试

重点覆盖：

```text
FailureClassifier
RecoveryPolicy
RuntimeFailureRepository
UserInputRequestService
PlanService 重新规划更新
模型输出拒绝错误映射
恢复预算扣减
```

### 8.2 集成测试

重点覆盖：

```text
非法模型 JSON -> 重试 -> 成功
结构化校验失败 -> 重试 -> 成功
unsupported tool intent -> 重新规划 -> 成功
workspace 越界访问 -> RuntimeRejected -> 重新规划或用户输入
验证失败 -> 追加修复计划 -> 再验证
恢复预算耗尽 -> AgentFailed
用户输入请求 -> answer -> AgentRunResumed
```

### 8.3 手工 CLI 测试

准备场景：

```text
真实 HTTP LLM provider
故意让模型返回不支持的工具动作
故意让模型请求越界路径
故意让验证命令失败
故意阻止一个命令策略
```

检查：

```text
agent status
agent events
agent failures
agent inputs
agent answer
agent report
```

## 9. 阶段 2 验收标准

阶段 2 完成后，应满足：

```text
每次模型调用都有开始、完成或失败事件
每次 Runtime 拒绝都有结构化失败记录
模型输出非法时可以有限重试
模型提出不支持动作时可以重新决策或重新规划
工具被权限或策略阻止时不会默认静默失败
验证失败时可以触发后续修复计划
任务可以进入 WAITING_USER_INPUT
用户回答后任务可以恢复
恢复预算耗尽时任务清晰失败
最终报告包含失败恢复摘要
API / CLI 可以查询失败和恢复轨迹
```

## 10. 风险和约束

主要风险：

```text
恢复逻辑如果直接塞进 DefaultAgentLoopExecutor，会让固定 Loop 过早复杂化
重新规划如果没有预算控制，会导致无限追加 PlanItem
用户输入和审批语义混淆，会让任务暂停原因不清楚
模型重试 prompt 如果携带过多上下文，会增加成本且降低稳定性
```

约束建议：

```text
先保持固定 Loop，不提前引入通用状态机
恢复策略集中在 RecoveryPolicy，不散落到各工具服务
ApprovalRequest 和 UserInputRequest 严格分离
恢复预算全部配置化
所有恢复路径必须写 AuditEvent
```

## 11. 完成后的下一阶段入口

阶段 2 完成后，进入阶段 3 的条件是：

```text
固定 Loop 已经具备可靠失败恢复
事件流足够表达运行路径
AgentRun 暂停、恢复、失败和完成语义稳定
Plan 可以被更新和重新规划
Runtime 已经能基于结构化状态做分支决策
```

这些能力稳定后，再把固定 Loop 抽象为状态机和工作流内核会更自然。
