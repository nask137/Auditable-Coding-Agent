# 阶段 1 技术说明：单 Agent 执行闭环

## 1. 技术目标

本文档描述阶段 1 的技术设计。

阶段 1 要实现一个本地运行的单 Agent 编码 Runtime，支持：

```text
理解用户任务
探索 trusted workspace
制定结构化计划
执行单个计划项
调用基础工具
记录文件变更
申请和处理审批
执行验证命令
生成执行报告
记录结构化审计事件
```

技术基调：

```text
Spring Boot 4
Java 25
PostgreSQL
Flyway
轻量 CLI
PostgreSQL-only
```

## 2. 总体架构

```text
CLI Client
   |
   | HTTP / JSON
   v
Spring Boot Agent Service
   |
   |-- API Layer
   |-- Agent Runtime
   |-- LLM Gateway
   |-- Tool Runtime
   |-- Permission Runtime
   |-- Approval Runtime
   |-- Audit Runtime
   |-- Report Runtime
   |
   v
PostgreSQL
```

后端服务是唯一的业务执行入口。CLI 只负责调用 API 和展示结果。

LLM 只负责生成结构化判断和动作意图。所有实际执行都必须通过 Runtime。

## 3. 推荐模块划分

```text
com.agent
  common
  workspace
  task
  run
  plan
  step
  action
  llm
  tool
  tool.file
  tool.command
  tool.git
  permission
  approval
  audit
  validation
  report
  api
```

模块职责：

```text
workspace：trusted workspace 注册、路径边界、安全规则
task：用户任务生命周期
run：AgentRun 生命周期
plan：Plan / PlanItem 管理
step：AgentStep 管理
action：AgentAction 管理
llm：模型网关和结构化输出
tool：工具注册、工具调用、工具结果
permission：权限判断和风险评估
approval：审批请求和审批处理
audit：审计事件追加写入和查询
validation：验证结果记录
report：任务报告生成
api：REST API
```

## 4. 核心运行流程

阶段 1 使用固定 Agent Loop。

```text
1. 用户创建 Task
2. 用户启动 Task
3. Runtime 创建 AgentRun
4. Runtime 记录 AgentRunStarted
5. LLM 理解任务，生成 TaskUnderstanding
6. Runtime 探索 Workspace
7. LLM 生成 PlanDraft
8. Runtime 创建 Plan / PlanItem
9. Runtime 逐个执行 PlanItem
10. LLM 生成 AgentDecision
11. Runtime 校验 AgentAction
12. PermissionService 做权限判断
13. ToolRuntime 执行工具
14. Runtime 记录 ToolCall / ToolResult / FileChange / CommandExecution
15. Runtime 更新 PlanItem / AgentStep / AgentRun 状态
16. Runtime 执行验证或创建审批请求
17. Runtime 生成 TaskReport
18. Runtime 完成或失败 AgentRun
```

固定 Loop 伪代码：

```java
public void execute(UUID runId) {
    understandTask(runId);
    inspectWorkspace(runId);
    createPlan(runId);

    while (hasPendingPlanItems(runId) && withinLimits(runId)) {
        executeNextPlanItem(runId);
        observeResult(runId);
        updatePlanIfNeeded(runId);
    }

    validateIfNeeded(runId);
    finishRun(runId);
}
```

## 5. PostgreSQL-only 存储策略

阶段 1 不引入 Redis。

PostgreSQL 是唯一事实源：

```text
业务状态写 PostgreSQL
审计事件写 PostgreSQL
文件变更证据写 PostgreSQL
命令执行记录写 PostgreSQL
审批状态写 PostgreSQL
验证结果写 PostgreSQL
报告写 PostgreSQL
```

内存中只保留当前执行所需临时数据：

```text
当前 LLM 请求上下文
当前工具输出缓冲
当前 patch 计算对象
当前命令进程句柄
```

任何形成事实的动作，都必须同步写 PostgreSQL。

不采用以下模式：

```text
先写 Redis，再异步落 PostgreSQL
```

原因：

```text
审计事件不能依赖异步刷库
FileChange 和 AuditEvent 需要强一致关联
审批状态不能和真实执行状态脱节
MVP 应优先降低恢复和一致性复杂度
```

## 6. 数据库表设计

### 6.1 workspace

```sql
create table workspace (
  id uuid primary key,
  name text not null,
  root_path text not null,
  trusted boolean not null,
  allowed_operations jsonb not null,
  blocked_paths jsonb not null,
  sensitive_patterns jsonb not null,
  created_at timestamptz not null,
  last_used_at timestamptz
);
```

### 6.2 task

```sql
create table task (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  title text not null,
  user_request text not null,
  status text not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
```

### 6.3 agent_run

```sql
create table agent_run (
  id uuid primary key,
  task_id uuid not null references task(id),
  agent_mode text not null,
  status text not null,
  started_at timestamptz not null,
  finished_at timestamptz,
  failure_reason text,
  runtime_metadata jsonb not null
);
```

### 6.4 plan

```sql
create table plan (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null references agent_run(id),
  status text not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
```

### 6.5 plan_item

```sql
create table plan_item (
  id uuid primary key,
  plan_id uuid not null references plan(id),
  description text not null,
  status text not null,
  related_files jsonb not null,
  notes text,
  order_index int not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
```

### 6.6 agent_step

```sql
create table agent_step (
  id uuid primary key,
  run_id uuid not null references agent_run(id),
  plan_item_id uuid references plan_item(id),
  step_type text not null,
  status text not null,
  input_summary text,
  output_summary text,
  started_at timestamptz not null,
  finished_at timestamptz
);
```

### 6.7 agent_action

```sql
create table agent_action (
  id uuid primary key,
  step_id uuid not null references agent_step(id),
  action_type text not null,
  reason text not null,
  risk_level text not null,
  status text not null,
  created_at timestamptz not null
);
```

### 6.8 tool_call

```sql
create table tool_call (
  id uuid primary key,
  action_id uuid not null references agent_action(id),
  tool_name text not null,
  permission_level text not null,
  input_summary text not null,
  input_payload jsonb not null,
  status text not null,
  started_at timestamptz not null,
  finished_at timestamptz
);
```

### 6.9 tool_result

```sql
create table tool_result (
  id uuid primary key,
  tool_call_id uuid not null references tool_call(id),
  success boolean not null,
  output_summary text,
  output_payload jsonb,
  error_message text,
  metadata jsonb not null,
  created_at timestamptz not null
);
```

### 6.10 file_change

```sql
create table file_change (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  task_id uuid not null references task(id),
  run_id uuid not null references agent_run(id),
  step_id uuid not null references agent_step(id),
  action_id uuid references agent_action(id),
  path text not null,
  change_type text not null,
  reason text not null,
  diff text,
  before_hash text,
  after_hash text,
  base_revision text,
  observed_at timestamptz,
  patch_apply_status text not null,
  line_added int not null default 0,
  line_deleted int not null default 0,
  risk_level text not null,
  approval_id uuid,
  created_at timestamptz not null
);
```

### 6.11 command_execution

```sql
create table command_execution (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  task_id uuid not null references task(id),
  run_id uuid not null references agent_run(id),
  step_id uuid not null references agent_step(id),
  action_id uuid references agent_action(id),
  command text not null,
  executable text not null,
  arguments jsonb not null,
  working_directory text not null,
  policy_type text not null,
  risk_level text not null,
  approval_id uuid,
  status text not null,
  exit_code int,
  output_summary text,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz not null
);
```

### 6.12 approval_request

```sql
create table approval_request (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null references agent_run(id),
  step_id uuid references agent_step(id),
  action_id uuid references agent_action(id),
  approval_type text not null,
  reason text not null,
  risk_level text not null,
  affected_files jsonb not null,
  command text,
  working_directory text,
  patch_preview text,
  status text not null,
  created_at timestamptz not null,
  resolved_at timestamptz,
  resolved_by text,
  resolution_reason text
);
```

### 6.13 audit_event

```sql
create table audit_event (
  id uuid primary key,
  task_id uuid references task(id),
  run_id uuid references agent_run(id),
  step_id uuid references agent_step(id),
  action_id uuid references agent_action(id),
  event_type text not null,
  actor text not null,
  level text not null,
  occurred_at timestamptz not null,
  input_summary text,
  output_summary text,
  related_files jsonb not null,
  related_tool_call_id uuid,
  related_approval_id uuid,
  related_command_id uuid,
  related_file_change_id uuid,
  permission_level text,
  risk_level text,
  approval_status text,
  success boolean,
  error_code text,
  error_message text,
  metadata jsonb not null
);
```

### 6.14 validation_result

```sql
create table validation_result (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null references agent_run(id),
  step_id uuid references agent_step(id),
  command_id uuid references command_execution(id),
  validation_type text not null,
  success boolean not null,
  summary text not null,
  created_at timestamptz not null
);
```

### 6.15 task_report

```sql
create table task_report (
  id uuid primary key,
  task_id uuid not null references task(id),
  run_id uuid not null references agent_run(id),
  content_md text not null,
  created_at timestamptz not null
);
```

### 6.16 command_policy

```sql
create table command_policy (
  id uuid primary key,
  workspace_id uuid not null references workspace(id),
  policy_type text not null,
  executable text not null,
  args_pattern jsonb not null,
  cwd_scope text not null,
  allow_pipe boolean not null,
  allow_redirect boolean not null,
  allow_background boolean not null,
  env_policy jsonb not null,
  enabled boolean not null,
  created_at timestamptz not null,
  updated_at timestamptz not null
);
```

## 7. 核心枚举

### 7.1 TaskStatus

```text
CREATED
RUNNING
WAITING_APPROVAL
WAITING_USER_INPUT
COMPLETED
FAILED
CANCELLED
```

### 7.2 AgentRunStatus

```text
RUNNING
WAITING_APPROVAL
WAITING_USER_INPUT
COMPLETED
FAILED
CANCELLED
```

### 7.3 PlanItemStatus

```text
PENDING
IN_PROGRESS
COMPLETED
FAILED
SKIPPED
```

### 7.4 StepType

```text
UNDERSTAND_TASK
INSPECT_WORKSPACE
CREATE_PLAN
EXECUTE_PLAN_ITEM
OBSERVE_RESULT
VALIDATE
FINISH
FAIL
```

### 7.5 ActionType

```text
CALL_MODEL
CALL_TOOL
REQUEST_APPROVAL
UPDATE_PLAN
RUN_VALIDATION
ASK_USER
FINISH
FAIL
```

### 7.6 RiskLevel

```text
LOW
MEDIUM
HIGH
CRITICAL
```

### 7.7 PermissionDecisionType

```text
ALLOW
REQUIRE_APPROVAL
BLOCK
```

## 8. LLM Gateway 设计

业务层不直接依赖具体模型 SDK。

```java
public interface LlmGateway {
    TaskUnderstanding understandTask(TaskContext context);
    PlanDraft createPlan(PlanningContext context);
    AgentDecision decideNextAction(ExecutionContext context);
    ValidationDecision suggestValidation(ValidationContext context);
    FinalReportDraft generateReport(ReportContext context);
}
```

LLM 输出必须结构化，并由 Runtime 校验。

### 8.1 TaskUnderstanding

```json
{
  "summary": "用户希望修复 UserService 的空指针问题并运行测试",
  "taskType": "BUG_FIX",
  "constraints": [
    "只修改必要文件",
    "运行测试验证"
  ],
  "initialSearchHints": [
    "UserService",
    "UserServiceTest"
  ]
}
```

### 8.2 PlanDraft

```json
{
  "items": [
    {
      "description": "搜索 UserService 和相关测试",
      "relatedFiles": [],
      "notes": "先定位实现和测试位置"
    },
    {
      "description": "修改目标方法并补充测试",
      "relatedFiles": [
        "src/main/java/com/example/UserService.java",
        "src/test/java/com/example/UserServiceTest.java"
      ],
      "notes": "保持修改范围小"
    },
    {
      "description": "运行测试验证",
      "relatedFiles": [],
      "notes": "优先运行相关测试"
    }
  ]
}
```

### 8.3 AgentDecision

```json
{
  "planItemId": "00000000-0000-0000-0000-000000000000",
  "actions": [
    {
      "type": "READ_FILE",
      "reason": "查看目标方法现有实现",
      "input": {
        "path": "src/main/java/com/example/UserService.java"
      }
    }
  ]
}
```

Runtime 必须校验：

```text
action type 是否支持
input schema 是否合法
路径是否在 workspace 内
权限是否允许
是否触发审批或阻止
```

不保存完整 chain-of-thought，只保存：

```text
decision summary
reason
input context summary
output summary
prompt version
model
token usage
```

## 9. Tool Runtime 设计

工具接口：

```java
public interface AgentTool<I, O> {
    String name();
    PermissionLevel requiredPermission();
    ToolResult<O> execute(I input, ToolExecutionContext context);
}
```

工具调用统一经过：

```text
AgentAction
→ PermissionService
→ ToolCall 记录
→ 工具执行
→ ToolResult 记录
→ AuditEvent 记录
```

第一阶段工具：

```text
list_files
read_file
search_text
create_file
apply_patch
run_command
git_status
git_diff
```

## 10. 文件工具设计

### 10.1 路径安全

所有文件工具执行前必须：

```text
解析相对路径
规范化路径
解析真实路径
判断是否在 trusted workspace 内
阻止路径穿越
阻止 workspace 外路径
阻止 .git 目录修改
匹配敏感文件规则
```

workspace 外路径第一阶段直接 BLOCK，不进入普通审批。

### 10.2 read_file

规则：

```text
普通文件读取：ALLOW
敏感文件读取：REQUIRE_APPROVAL 或 BLOCK
强敏感文件读取：BLOCK
workspace 外路径：BLOCK
```

审计：

```text
FileRead
PermissionChecked
PermissionAllowed / PermissionApprovalRequired / PermissionBlocked
```

### 10.3 apply_patch

第一阶段建议使用结构化 search / replace patch，而不是任意覆盖写。

输入示例：

```json
{
  "path": "src/main/java/com/example/UserService.java",
  "reason": "为 null 输入增加防御性判断",
  "edits": [
    {
      "oldText": "return user.getName();",
      "newText": "return user == null ? null : user.getName();"
    }
  ]
}
```

执行要求：

```text
读取变更前内容
计算 beforeHash
应用 patch
计算 afterHash
生成 unified diff
统计 lineAdded / lineDeleted
判断是否大范围 patch
写入 FileChange
写入 FileModified 或 FileCreated 事件
```

如果 patch 失败：

```text
记录 ToolCallFailed
记录 patchApplyStatus=FAILED
重新读取目标文件
允许有限次数重试
超过次数后 AgentRun 失败
```

## 11. 命令工具设计

命令执行不得直接使用自由 shell 字符串。

推荐结构化输入：

```json
{
  "executable": "./mvnw",
  "arguments": ["test"],
  "workingDirectory": ".",
  "reason": "验证 Java 测试是否通过"
}
```

执行方式：

```text
ProcessBuilder
不经 shell
不使用 powershell -Command
不使用 bash -c
```

命令策略：

```text
ALLOWLIST：直接执行
APPROVAL_REQUIRED：创建审批请求并暂停
BLOCKED：阻止执行
```

默认阻止：

```text
rm -rf
del /s
format
chmod -R
curl | sh
远程脚本直接执行
删除 workspace 根目录
破坏性 Git 操作
```

命令执行记录：

```text
command
executable
arguments
workingDirectory
policyType
riskLevel
approvalId
status
exitCode
outputSummary
startedAt
finishedAt
```

stdout / stderr 第一阶段只保存摘要，避免审计日志泄漏敏感信息。

## 12. 权限和审批设计

权限判断接口：

```java
public interface PermissionService {
    PermissionDecision check(AgentAction action, PermissionRequest request);
}
```

返回：

```text
ALLOW
REQUIRE_APPROVAL
BLOCK
```

审批触发条件：

```text
删除文件
移动或重命名文件
读取敏感文件
修改敏感文件
修改高影响文件
大范围 patch
执行白名单外命令
安装依赖
启动长期运行进程
网络访问
Git 写操作
```

审批流程：

```text
1. PermissionService 返回 REQUIRE_APPROVAL
2. Runtime 创建 ApprovalRequest
3. 写入 ApprovalRequested 事件
4. Task / AgentRun 状态改为 WAITING_APPROVAL
5. AgentLoop 暂停
6. 用户通过 CLI approve 或 deny
7. approve 后恢复执行
8. deny 后重新规划、暂停或失败
```

阶段 1 可以先采用：

```text
deny 后任务进入 FAILED 或 WAITING_USER_INPUT
```

不必一开始实现复杂重新规划。

## 13. Audit Runtime 设计

AuditEvent 是任务追踪、回放和解释的基础。

阶段 1 必须记录：

```text
TaskCreated
AgentRunStarted
TaskUnderstood
PlanCreated
PlanUpdated
StepStarted
StepCompleted
ToolCallRequested
ToolCallCompleted
ToolCallFailed
FileRead
FileCreated
FileModified
FileAccessBlocked
CommandRequested
CommandExecuted
CommandBlocked
PermissionChecked
PermissionAllowed
PermissionApprovalRequired
PermissionBlocked
ApprovalRequested
ApprovalGranted
ApprovalDenied
ValidationStarted
ValidationCompleted
AgentFinished
AgentFailed
```

AuditService：

```java
public interface AuditService {
    void append(AuditEvent event);
}
```

原则：

```text
AuditEvent 只追加
业务状态可以更新
事件不保存敏感文件完整内容
事件不保存完整 prompt
事件不保存完整 stdout/stderr
事件保存摘要、引用和结构化 metadata
```

## 14. API 设计

### 14.1 Workspace API

```text
POST /api/workspaces
GET  /api/workspaces
GET  /api/workspaces/{workspaceId}
```

### 14.2 Task API

```text
POST /api/tasks
GET  /api/tasks/{taskId}
POST /api/tasks/{taskId}/start
POST /api/tasks/{taskId}/cancel
```

### 14.3 Run / Plan API

```text
GET /api/runs/{runId}
GET /api/runs/{runId}/plan
GET /api/runs/{runId}/steps
```

### 14.4 Observation API

```text
GET /api/tasks/{taskId}/events
GET /api/tasks/{taskId}/changes
GET /api/tasks/{taskId}/report
```

### 14.5 Approval API

```text
GET  /api/approvals
GET  /api/approvals/{approvalId}
POST /api/approvals/{approvalId}/approve
POST /api/approvals/{approvalId}/deny
```

### 14.6 Command Policy API

```text
GET    /api/workspaces/{workspaceId}/command-policies
POST   /api/workspaces/{workspaceId}/command-policies
DELETE /api/command-policies/{policyId}
```

## 15. CLI 设计

CLI 建议使用 Java 25 + picocli。

CLI 不承载业务逻辑，只调用后端 API。

命令：

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

## 16. 事务和一致性

数据库状态和审计事件应尽量在同一事务中写入。

示例：

```text
创建 Task
→ insert task
→ insert audit_event TaskCreated
→ commit
```

文件系统操作不能被数据库事务回滚，需要谨慎处理。

文件 patch 推荐流程：

```text
1. 创建 AgentAction
2. 权限判断
3. 写 ToolCallRequested
4. 读取文件并计算 beforeHash
5. 应用 patch
6. 计算 afterHash 和 diff
7. 写 FileChange
8. 写 ToolResult
9. 写 FileModified
10. 更新 Step / PlanItem 状态
```

如果文件操作成功但数据库写入失败：

```text
AgentRun 标记为 FAILED
系统日志记录严重错误
后续阶段再引入恢复扫描机制
```

阶段 1 不实现完整补偿事务。

## 17. 验证机制

验证通过 `run_command` 实现。

Agent 可以建议验证命令：

```text
./mvnw test
mvn test
gradle test
npm test
pytest
go test ./...
cargo test
```

Runtime 根据 command policy 决定：

```text
ALLOWLIST：执行
APPROVAL_REQUIRED：创建审批
BLOCKED：阻止
```

ValidationResult 记录：

```text
validationType
commandId
success
summary
createdAt
```

## 18. 报告生成

任务结束后生成 Markdown 报告。

报告内容：

```text
任务目标
执行结果
计划执行情况
读取的关键文件
创建或修改的文件
文件 diff 摘要
命令执行记录
审批记录
验证结果
失败原因
后续建议
```

报告写入 `task_report.content_md`。

## 19. 配置项

建议配置：

```yaml
agent:
  loop:
    max-steps: 20
    max-tool-calls: 50
    max-file-changes: 5
    max-patch-lines: 300
    max-consecutive-failures: 3
  command:
    timeout-seconds: 120
  file:
    max-read-bytes: 200000
    blocked-paths:
      - ".git"
    approval-sensitive-patterns:
      - ".env"
      - ".env.*"
    blocked-sensitive-patterns:
      - "*.pem"
      - "*.key"
      - "id_rsa"
      - "id_ed25519"
      - "*.p12"
      - "*.jks"
      - "credentials"
      - "secrets"
```

## 20. 测试策略

### 20.1 单元测试

重点：

```text
路径规范化
workspace 越界拦截
敏感文件匹配
命令策略匹配
危险命令阻止
patch 应用
diff 统计
PlanItem 状态流转
AuditEvent 构造
```

### 20.2 集成测试

使用 Testcontainers + PostgreSQL。

重点：

```text
Workspace API
Task API
AgentRun 创建
AuditEvent 写入和查询
FileChange 写入
ApprovalRequest 创建和处理
CommandExecution 写入
ValidationResult 写入
```

### 20.3 端到端测试

准备一个小型 Java 示例项目。

验证：

```text
注册 workspace
提交任务
Agent 搜索文件
Agent 读取目标文件
Agent 生成计划
Agent 应用 patch
Agent 记录 diff
Agent 执行或申请测试命令
Agent 记录验证结果
Agent 生成报告
```

## 21. 阶段 1 技术验收标准

阶段 1 技术完成后，应满足：

```text
PostgreSQL 是唯一事实源
所有核心领域对象都有持久化表
AgentRun 可以启动和结束
固定 Agent Loop 可以执行
LLM 输出被结构化校验
文件工具不能越过 workspace 边界
普通文件读写可执行
文件变更有 diff 和 hash
命令执行经过结构化策略判断
白名单外命令进入审批
危险命令被阻止
审批可以通过 API 和 CLI 处理
验证结果可记录
审计事件可按时间顺序查询
任务报告可生成和查询
```
