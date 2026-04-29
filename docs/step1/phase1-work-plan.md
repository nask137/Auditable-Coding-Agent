# 阶段 1 工作计划：单 Agent 执行闭环

## 1. 阶段目标

阶段 1 的目标是实现一个最小可用的单 Agent 编码执行闭环。

本阶段不追求复杂状态机、可视化编排、多 Agent、长期记忆或完整 RAG，而是优先证明：

```text
Agent 可以在 trusted workspace 内完成一个小型真实编码任务，
并且任务、计划、步骤、工具调用、文件变更、命令执行、审批和验证结果都可以被追踪。
```

阶段 1 采用以下技术基调：

```text
Spring Boot 4
Java 25
PostgreSQL
轻量 CLI 客户端
PostgreSQL 作为唯一事实源
MVP 不引入 Redis
```

## 2. 阶段边界

### 2.1 本阶段要做

```text
注册 trusted workspace
创建和启动 Task
创建 AgentRun
执行固定单 Agent Loop
生成结构化 Plan
执行 PlanItem
调用基础文件工具
调用基础命令工具
调用 Git 只读工具
记录 AgentStep 和 AgentAction
记录 ToolCall 和 ToolResult
记录 FileChange 和 diff
记录 CommandExecution
支持 ApprovalRequest
记录 AuditEvent
记录 ValidationResult
生成 TaskReport
提供轻量 CLI
```

### 2.2 本阶段暂不做

```text
Redis
复杂工作流 DSL
状态机引擎
多 Agent
插件市场
Web 控制台
桌面应用
IDE 插件
长期 Memory
完整 RAG
复杂代码语义分析
团队权限
事件签名和防篡改哈希链
云端执行环境
```

## 3. 核心闭环

阶段 1 使用固定流程：

```text
Start
→ UnderstandTask
→ InspectWorkspace
→ CreatePlan
→ ExecuteNextPlanItem
→ ObserveResult
→ UpdatePlan
→ Validate
→ Finish
```

退出条件：

```text
任务完成
验证通过
需要用户审批
需要用户输入
用户拒绝审批
达到最大步骤数
达到最大工具调用数
达到最大文件修改数
达到最大连续失败次数
发生不可恢复错误
任务被用户取消
```

建议默认限制：

```text
最大 AgentStep 数量：20
最大工具调用次数：50
最大文件修改数量：5
单次 patch 最大行数：300
最大连续失败次数：3
命令默认超时：120 秒
```

## 4. 里程碑拆分

### Milestone 1：工程基础和数据库

目标：

```text
建立 Spring Boot 4 后端工程
接入 PostgreSQL
接入 Flyway
建立基础领域表
提供 Workspace 和 Task 基础 API
```

交付内容：

```text
后端工程可启动
数据库迁移可执行
Workspace 可创建和查询
Task 可创建和查询
基础枚举和通用 ID / 时间模型
```

验收标准：

```text
可以注册 trusted workspace
可以创建 Task
可以查询 Task 状态
数据库表由 Flyway 自动创建
```

### Milestone 2：审计事件和运行实例

目标：

```text
建立 AgentRun、AgentStep、AgentAction、AuditEvent 基础链路
```

交付内容：

```text
AgentRun 创建和启动
AgentStep 创建和完成
AgentAction 创建和完成
AuditEvent 追加写入
TaskCreated / AgentRunStarted / StepStarted / StepCompleted 事件
```

验收标准：

```text
启动一次 Task 会创建 AgentRun
启动过程产生结构化 AuditEvent
AuditEvent 可以按时间顺序查询
```

### Milestone 3：计划机制

目标：

```text
支持结构化 Plan 和 PlanItem
```

交付内容：

```text
Plan 表
PlanItem 表
PlanCreated / PlanUpdated 事件
PlanItem 状态流转
```

验收标准：

```text
AgentRun 可以绑定一个 Plan
Plan 包含多个有序 PlanItem
PlanItem 可以从 PENDING 流转到 IN_PROGRESS / COMPLETED / FAILED
```

### Milestone 4：文件工具闭环

目标：

```text
实现 trusted workspace 内的基础文件工具
```

交付内容：

```text
list_files
read_file
search_text
create_file
apply_patch
FileChange
文件路径规范化
workspace 越界拦截
敏感文件基础判断
FileRead / FileCreated / FileModified / FileAccessBlocked 事件
```

验收标准：

```text
可以列出 workspace 内文件
可以读取普通文件
可以搜索文本
可以创建普通文件
可以对普通文件应用小 patch
workspace 外路径被阻止
.git 目录修改被阻止
每次文件变更都有 diff、beforeHash、afterHash、lineAdded、lineDeleted
```

### Milestone 5：LLM 接入和固定 Agent Loop

目标：

```text
接入 LLM Gateway，让 Agent 可以理解任务、生成计划和产生结构化动作意图
```

交付内容：

```text
LlmGateway 接口
TaskUnderstanding 结构
PlanDraft 结构
AgentDecision 结构
固定 AgentLoopExecutor
结构化输出校验
ModelCallStarted / ModelCallCompleted / TaskUnderstood 事件
```

验收标准：

```text
用户提交自然语言任务后，系统可以生成任务理解
系统可以生成结构化 Plan
系统可以根据 PlanItem 产生下一步工具动作
LLM 不能绕过 Runtime 直接执行文件或命令操作
```

### Milestone 6：命令工具、策略和审批

目标：

```text
实现受控命令执行和审批暂停机制
```

交付内容：

```text
run_command
CommandPolicy
CommandExecution
ApprovalRequest
approve / deny API
PermissionDecision
PermissionChecked / PermissionAllowed / PermissionApprovalRequired / PermissionBlocked 事件
CommandRequested / CommandExecuted / CommandBlocked 事件
ApprovalRequested / ApprovalGranted / ApprovalDenied 事件
```

验收标准：

```text
白名单命令可以执行
白名单外命令会创建审批请求
危险命令会被阻止
用户批准后任务可以继续
用户拒绝后任务暂停或失败，且不得执行被拒绝动作
```

### Milestone 7：验证和任务报告

目标：

```text
支持验证命令和最终执行报告
```

交付内容：

```text
ValidationResult
ValidationStarted / ValidationCompleted 事件
TaskReport
report 查询 API
```

验收标准：

```text
Agent 可以建议测试命令
测试命令按命令策略执行或进入审批
验证结果被记录
任务结束后生成 Markdown 报告
报告包含计划、变更、命令、审批、验证和最终状态
```

### Milestone 8：CLI 和端到端验收

目标：

```text
提供轻量 CLI，跑通完整 MVP 场景
```

交付内容：

```text
agent workspace add
agent workspace list
agent run
agent status
agent events
agent diff
agent approvals
agent approve
agent deny
agent report
```

验收标准：

```text
用户可以通过 CLI 注册 workspace
用户可以通过 CLI 提交任务
用户可以通过 CLI 查看事件
用户可以通过 CLI 查看 diff
用户可以通过 CLI 处理审批
用户可以通过 CLI 查看最终报告
```

## 5. API 实施范围

### 5.1 Workspace API

```text
POST /api/workspaces
GET  /api/workspaces
GET  /api/workspaces/{workspaceId}
```

### 5.2 Task API

```text
POST /api/tasks
GET  /api/tasks/{taskId}
POST /api/tasks/{taskId}/start
POST /api/tasks/{taskId}/cancel
```

### 5.3 Run / Plan API

```text
GET /api/runs/{runId}
GET /api/runs/{runId}/plan
GET /api/runs/{runId}/steps
```

### 5.4 Observation API

```text
GET /api/tasks/{taskId}/events
GET /api/tasks/{taskId}/changes
GET /api/tasks/{taskId}/report
```

### 5.5 Approval API

```text
GET  /api/approvals
GET  /api/approvals/{approvalId}
POST /api/approvals/{approvalId}/approve
POST /api/approvals/{approvalId}/deny
```

### 5.6 Command Policy API

```text
GET    /api/workspaces/{workspaceId}/command-policies
POST   /api/workspaces/{workspaceId}/command-policies
DELETE /api/command-policies/{policyId}
```

## 6. CLI 实施范围

第一阶段 CLI 只作为后端 API 客户端，不承载 Agent Runtime 逻辑。

建议命令：

```text
agent workspace add <path>
agent workspace list

agent run "<task>" --workspace <workspaceId>
agent status <taskId>
agent events <taskId>
agent diff <taskId>
agent report <taskId>

agent approvals
agent approve <approvalId>
agent deny <approvalId>

agent command allow --workspace <workspaceId> --exec ./mvnw --args test
agent command list --workspace <workspaceId>
```

## 7. PostgreSQL-only 决策

MVP 阶段不引入 Redis。

阶段 1 中，PostgreSQL 是唯一事实源：

```text
Task 状态写 PostgreSQL
AgentRun 状态写 PostgreSQL
Plan 和 PlanItem 写 PostgreSQL
AgentStep 和 AgentAction 写 PostgreSQL
ToolCall 和 ToolResult 写 PostgreSQL
FileChange 写 PostgreSQL
CommandExecution 写 PostgreSQL
ApprovalRequest 写 PostgreSQL
AuditEvent 写 PostgreSQL
ValidationResult 写 PostgreSQL
TaskReport 写 PostgreSQL
```

内存中只允许保留当前执行所需的临时对象，例如：

```text
当前 LLM 请求上下文
当前工具执行过程中的临时输出
当前 patch 计算中的临时内容
```

任何形成事实的动作都必须同步写入 PostgreSQL。

Redis 延后到以下场景再考虑：

```text
实时任务进度推送
WebSocket / SSE 事件广播
多实例执行锁
任务队列
高频状态缓存
模型上下文缓存
限流计数
后台异步调度
```

## 8. 测试计划

### 8.1 单元测试

重点覆盖：

```text
路径规范化
workspace 边界判断
敏感文件匹配
命令策略匹配
危险命令阻止
patch 应用
diff 统计
PlanItem 状态流转
AuditEvent 构造
```

### 8.2 集成测试

使用 Testcontainers 启动 PostgreSQL。

重点覆盖：

```text
Workspace API
Task API
AgentRun 创建
AuditEvent 写入和查询
FileChange 写入
ApprovalRequest 创建和处理
CommandExecution 写入
```

### 8.3 端到端测试

准备一个小型 Java 示例项目，验证：

```text
Agent 能搜索文件
Agent 能读取目标代码
Agent 能生成计划
Agent 能修改普通文件
Agent 能记录 diff
Agent 能申请或执行测试命令
Agent 能记录验证结果
Agent 能生成报告
```

## 9. 阶段 1 验收标准

阶段 1 完成后，应满足：

```text
可以注册 trusted workspace
可以通过 CLI 创建并启动任务
Agent 可以理解任务并生成结构化 Plan
Agent 可以读取 workspace 内普通文件
Agent 可以搜索文本
Agent 可以创建和修改 workspace 内普通文件
Agent 不能访问 workspace 外路径
Agent 不能修改 .git 目录
Agent 删除或移动文件必须审批
Agent 执行白名单外命令必须审批
Agent 执行危险命令会被阻止
每个关键动作都有 AuditEvent
每次文件变更都有 FileChange 和 diff
每次命令申请和执行都有 CommandExecution
每次审批都有 ApprovalRequest 和审批事件
验证结果可查询
任务结束后生成 TaskReport
用户可以按时间顺序查看主要执行事件
```

## 10. 推荐完成顺序

建议按以下顺序推进：

```text
1. Spring Boot 4 + PostgreSQL + Flyway 工程基础
2. Workspace / Task / AgentRun / AuditEvent
3. Plan / PlanItem / AgentStep / AgentAction
4. 文件工具和 FileChange
5. LLM Gateway 和固定 Agent Loop
6. CommandPolicy / run_command / ApprovalRequest
7. ValidationResult / TaskReport
8. CLI
9. 端到端验收项目
```

这个顺序能让系统尽早形成可运行骨架，并逐步把 Agent 能力放进受控 Runtime 中。
