# 第一版 MVP 规范说明

## 1. 文档目标

本文档定义可审计编码智能体第一版 MVP 的范围、目标、非目标、核心能力、约束规则和验收标准。

MVP 的目标不是做完整的编码智能体平台，而是验证以下核心假设：

> 一个本地运行的 Java 后端 Agent Service，可以在用户信任的 workspace 内完成小型编码任务，并且整个过程可追踪、可审批、可审计。

## 2. MVP 产品形态

第一版采用如下产品形态：

```text
本地 Java 后端 Agent Service
+
轻量 CLI 客户端
+
本地 Workspace 权限配置
+
本地审计日志
```

后端服务是核心，CLI 是第一版交互入口。

后续 Web 应用、桌面应用、IDE 插件都应作为客户端接入同一个后端能力，而不是重新实现 Agent Runtime。

## 3. MVP 目标用户

第一版面向个人开发者。

典型用户场景：

- 用户在本地项目中启动 Agent Service
- 用户通过 CLI 提交一个小型编码任务
- Agent 在 trusted workspace 内读取代码
- Agent 制定计划并执行小步修改
- Agent 对高风险操作请求审批
- 用户查看 diff、审批命令、查看最终报告

第一版暂不面向团队协作。

## 4. MVP 核心目标

第一版需要完成以下目标：

- 支持注册 trusted workspace
- 支持创建和启动编码任务
- 支持单 Agent 执行闭环
- 支持基础文件工具
- 支持基础命令申请和执行
- 支持命令白名单
- 支持高风险动作审批
- 支持结构化计划
- 支持文件变更记录
- 支持审计事件日志
- 支持任务执行报告

## 5. MVP 非目标

第一版明确不做：

- 复杂 Web 控制台
- 桌面应用
- IDE 插件
- 多 Agent 协作
- 工作流可视化编排
- 完整 LangGraph 类图引擎
- 完整 RAG 系统
- 长期记忆系统
- 团队权限体系
- 远程执行环境
- 插件市场
- 复杂代码语义分析
- 自动执行任意 shell 命令
- 自动访问任意本地目录

这些能力可以作为后续阶段演进内容。

## 6. MVP 核心流程

第一版核心执行流程：

```text
用户提交任务
→ 创建 Task
→ 启动 AgentRun
→ 理解任务
→ 探索 Workspace
→ 制定 Plan
→ 执行 PlanItem
→ 调用工具
→ 记录文件变更
→ 必要时请求审批
→ 必要时执行验证命令
→ 生成任务报告
→ 完成或失败
```

每个关键动作都必须写入审计日志。

## 7. MVP Agent Loop

第一版可以采用固定 Agent Loop，不需要复杂工作流引擎。

建议流程：

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

循环退出条件：

```text
任务完成
验证通过
需要用户输入
用户拒绝审批
达到最大循环次数
发生不可恢复错误
任务被取消
```

建议第一版限制：

```text
最大 AgentStep 数量：20
最大工具调用次数：50
最大文件修改数量：5
单次 patch 最大行数：300
最大连续失败次数：3
```

超过限制应暂停并请求用户确认。

## 8. MVP 核心领域对象

第一版优先实现以下对象：

```text
Workspace
Task
AgentRun
AgentState
Plan
PlanItem
AgentStep
AgentAction
ToolCall
ToolResult
FileChange
CommandExecution
ApprovalRequest
AuditEvent
ValidationResult
```

第一版暂缓实现：

```text
MemoryEntry
WorkflowDefinition
WorkflowNode
WorkflowEdge
AgentProfile
ToolPlugin
CodeSymbol
CodeReference
AuditReportTemplate
```

## 9. MVP 工具范围

第一版工具保持克制，只实现编码任务最小闭环需要的能力。

### 9.1 文件工具

```text
list_files
read_file
search_text
apply_patch
create_file
```

说明：

- `list_files`：列出 workspace 内文件
- `read_file`：读取普通文件
- `search_text`：搜索文本
- `apply_patch`：对已有文件应用 patch
- `create_file`：创建新文件

第一版不建议开放任意 `write_file` 覆盖式写入，优先使用 `apply_patch`，更利于审计。

### 9.2 命令工具

```text
run_command
```

说明：

- 只能在 trusted workspace 内执行
- 默认不自动执行
- 白名单命令可执行
- 白名单外命令必须审批
- 阻止高危命令

### 9.3 Git 工具

```text
git_status
git_diff
```

第一版只支持 Git 只读能力。

暂不支持：

```text
git_commit
git_checkout
git_merge
git_rebase
git_reset
```

### 9.4 验证工具

第一版通过 `run_command` 执行验证命令。

建议支持：

```text
mvn test
./mvnw test
gradle test
npm test
npm run test
pytest
go test ./...
cargo test
```

是否执行取决于命令白名单或用户审批。

## 10. MVP 权限规则

### 10.1 Workspace 规则

第一版必须满足：

- Agent 只能访问 trusted workspace
- 文件路径必须规范化后再判断边界
- 禁止路径穿越
- 默认禁止访问 workspace 外路径
- 默认禁止修改 `.git` 目录

### 10.2 文件规则

默认允许：

```text
读取 trusted workspace 内普通文件
创建 trusted workspace 内普通文件
修改 trusted workspace 内普通文件
```

默认需要审批：

```text
删除文件
移动或重命名文件
修改敏感文件
修改构建或依赖配置
大范围文件修改
单次 patch 行数过多
```

默认阻止：

```text
访问 workspace 外路径
修改 .git 目录
路径穿越
写入系统目录
```

### 10.3 命令规则

命令分为三类：

```text
ALLOWLIST
APPROVAL_REQUIRED
BLOCKED
```

默认允许：

```text
用户配置的白名单命令
```

默认需要审批：

```text
未加入白名单但不属于明确危险的命令
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

## 11. MVP 审批范围

第一版必须支持审批：

```text
删除文件
移动或重命名文件
修改敏感文件
修改高影响文件
大范围 patch
执行白名单外命令
安装依赖
启动长期运行进程
网络访问
```

审批请求必须展示：

```text
动作类型
申请原因
风险等级
影响文件
命令内容
工作目录
patch 摘要
允许 / 拒绝 操作入口
```

用户拒绝审批后，Agent 应：

```text
记录 ApprovalDenied
暂停任务或重新规划
不能继续执行被拒绝动作
```

## 12. MVP 审计日志范围

第一版必须记录以下事件：

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

第一版审计事件应至少包含：

```text
eventId
taskId
runId
stepId
eventType
actor
timestamp
inputSummary
outputSummary
relatedFiles
permissionLevel
riskLevel
approvalStatus
success
errorMessage
metadata
```

## 13. MVP 本地存储建议

第一版可以使用本地文件存储，降低实现成本。

建议结构：

```text
.agent/
  workspaces.json
  command-policies.json
  tasks/
    task_001/
      task.json
      runs/
        run_001/
          state.json
          plan.json
          events.jsonl
          file-changes.json
          approvals.json
          command-executions.json
          validation-results.json
          report.md
```

说明：

- `events.jsonl` 用于追加写入审计事件
- `state.json` 保存运行时状态
- `file-changes.json` 保存文件变更和 diff
- `report.md` 保存任务结束报告

后续可以迁移到 SQLite、PostgreSQL 或其他数据库。

## 14. MVP API 能力

第一版后端 API 可以按以下边界设计。

### 14.1 Workspace API

```text
POST /workspaces
GET  /workspaces
GET  /workspaces/{workspaceId}
```

### 14.2 Task API

```text
POST /tasks
GET  /tasks/{taskId}
POST /tasks/{taskId}/start
POST /tasks/{taskId}/cancel
```

### 14.3 Task Observation API

```text
GET /tasks/{taskId}/events
GET /tasks/{taskId}/changes
GET /tasks/{taskId}/report
```

### 14.4 Approval API

```text
GET  /approvals
GET  /approvals/{approvalId}
POST /approvals/{approvalId}/approve
POST /approvals/{approvalId}/deny
```

### 14.5 Command Policy API

```text
GET    /command-policies
POST   /command-policies/allowlist
DELETE /command-policies/allowlist/{id}
```

## 15. MVP CLI 能力

第一版 CLI 保持简单。

建议命令：

```text
agent workspace add <path>
agent workspace list

agent run "<task>"
agent status <taskId>
agent events <taskId>
agent diff <taskId>
agent report <taskId>

agent approvals
agent approve <approvalId>
agent deny <approvalId>

agent command allow <command>
agent command list
```

CLI 不需要承载复杂业务逻辑，只负责调用后端 API 并展示结果。

## 16. MVP 任务报告

任务结束后应生成报告。

报告建议包含：

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

报告用于帮助用户快速审计本次 Agent 行为。

## 17. MVP 成功场景

建议 MVP 的主要验收场景：

```text
用户在 CLI 中提交任务：
帮我给某个 Java Service 添加一个简单方法和对应单元测试。

Agent：
1. 在 trusted workspace 内搜索相关文件
2. 读取目标代码和测试代码
3. 生成简短执行计划
4. 修改或创建文件
5. 记录文件 diff
6. 申请执行测试命令
7. 用户审批后执行测试
8. 记录测试结果
9. 输出任务报告
```

验收要求：

- Agent 不访问 workspace 外路径
- Agent 不自动执行未授权命令
- Agent 的每次文件变更可追踪
- 用户可以查看完整执行事件
- 用户可以查看审批记录
- 用户可以查看最终任务报告

## 18. MVP 失败场景

第一版也必须能处理基本失败场景。

### 18.1 文件越界

如果 Agent 尝试访问 workspace 外文件：

```text
阻止访问
记录 FileAccessBlocked
记录 PermissionBlocked
通知用户
```

### 18.2 命令未授权

如果 Agent 尝试执行白名单外命令：

```text
创建 ApprovalRequest
记录 PermissionApprovalRequired
等待用户审批
```

### 18.3 用户拒绝审批

如果用户拒绝审批：

```text
记录 ApprovalDenied
Agent 重新规划或暂停任务
不得执行被拒绝动作
```

### 18.4 测试失败

如果测试失败：

```text
记录 ValidationCompleted(success=false)
Agent 分析失败摘要
最多尝试有限次数修复
超过次数后失败退出并生成报告
```

### 18.5 Patch 失败

如果 patch 无法应用：

```text
记录 ToolCallFailed
重新读取目标文件
尝试重新生成 patch
超过次数后失败退出
```

## 19. MVP 验收标准

第一版完成后，应满足：

- 可以注册 trusted workspace
- 可以通过 CLI 创建任务
- 可以启动一次 AgentRun
- Agent 可以读取 workspace 内文件
- Agent 可以创建和修改 workspace 内普通文件
- Agent 不能访问 workspace 外路径
- Agent 删除或移动文件必须审批
- Agent 执行白名单外命令必须审批
- Agent 执行危险命令会被阻止
- Agent 每一步都有审计事件
- Agent 文件变更可查看 diff
- Agent 命令执行结果可查看
- Agent 审批记录可查看
- Agent 任务结束后生成 report.md
- 用户可以按时间顺序回放主要执行事件

## 20. MVP 后续演进入口

MVP 完成后，可以自然演进到：

```text
阶段 1：更稳定的单 Agent 执行闭环
阶段 2：更完整的可审计运行时
阶段 3：状态机与工作流内核
阶段 4：项目记忆与代码理解
阶段 5：可视化编排与回放
阶段 6：多 Agent、插件化与产品化
```

MVP 阶段应避免过早引入复杂工作流和多 Agent。优先把单 Agent 在本地 trusted workspace 内的安全执行闭环做扎实。
