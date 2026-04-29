# 可审计编码智能体渐进式建设计划

## 1. 项目目标

本项目的目标不是简单做一个能调用工具的 Chat Agent，而是构建一个可控、可观察、可恢复、可审计的编码智能体。

它应该具备以下核心能力：

- 理解用户的编码任务
- 探索代码库并形成上下文
- 制定可执行计划
- 小步修改代码
- 调用工具进行验证
- 记录完整执行过程
- 在失败时恢复、重试或请求用户介入
- 让用户能够审计每一步决策、工具调用和文件变更

核心设计原则：

> LLM 负责判断、规划和生成；Runtime 负责状态、权限、流程、审计和恢复。

## 2. 总体阶段划分

建议分为六个阶段推进：

```text
阶段 0：概念与边界定义
阶段 1：单 Agent 执行闭环
阶段 2：可审计运行时
阶段 3：状态机与工作流内核
阶段 4：项目记忆与代码理解
阶段 5：可视化编排与回放
阶段 6：多 Agent、插件化与产品化
```

每个阶段都应该能独立交付一个可用版本，而不是等所有能力完成后才可用。

## 阶段 0：概念与边界定义

### 阶段目标

明确这个编码智能体做什么和不做什么，建立统一的领域模型，避免一开始陷入技术细节。

这个阶段的重点是定义产品边界、核心对象和审计模型。

### 核心任务

#### 1. 明确智能体定位

需要回答：

- 它是 CLI 工具、Web 应用、IDE 插件，还是后端服务？
- 第一版主要服务个人开发者，还是团队协作？
- 它是否默认可以修改文件？
- 它是否默认可以执行命令？
- 哪些行为必须经过用户审批？

建议第一版定位为：

```text
本地运行的可审计编码智能体，允许读取项目、生成计划、小步修改文件、运行验证命令，并完整记录执行过程。
```

#### 2. 定义核心对象

建议先抽象这些对象：

```text
Agent
Task
AgentState
AgentStep
AgentAction
ToolCall
ToolResult
Observation
Plan
PlanItem
FileChange
AuditLog
ApprovalRequest
ValidationResult
```

这些对象是后续所有能力的基础。

#### 3. 定义审计维度

可审计不是简单保存日志，而是要能回答这些问题：

- 用户提出了什么目标？
- Agent 如何理解这个目标？
- 它读取了哪些文件？
- 它为什么决定修改这些文件？
- 它调用了哪些工具？
- 工具返回了什么？
- 它实际改了哪些内容？
- 修改前后差异是什么？
- 它有没有运行验证？
- 验证是否通过？
- 失败后它做了什么？
- 哪些动作经过了用户确认？

#### 4. 定义权限等级

提前设计权限分级：

```text
READ_ONLY：只读项目文件
WORKSPACE_WRITE：允许修改工作区文件
SHELL_SAFE：允许运行测试、构建、lint
SHELL_RISKY：允许执行潜在破坏性命令
GIT_READ：允许查看 git 状态和 diff
GIT_WRITE：允许 commit、merge、rebase
NETWORK：允许访问外部网络
```

每个工具都应该绑定权限等级。

### 阶段产出

- 产品定位说明
- 核心领域模型草图
- 权限模型草案
- 审计日志字段草案
- 第一版 MVP 范围说明

### 验收标准

这个阶段完成后，你应该能够清楚描述：

```text
这个 Agent 能做什么
不能做什么
每一步如何记录
哪些动作需要审批
任务执行过程如何被还原
```

## 阶段 1：单 Agent 执行闭环

### 阶段目标

先实现一个最小可用的编码 Agent Loop。这个阶段不追求复杂状态机和可视化，只追求完成一次真实编码任务的闭环。

核心闭环：

```text
理解任务
→ 探索代码
→ 制定计划
→ 执行一步
→ 观察结果
→ 更新计划
→ 验证
→ 结束
```

### 核心任务

#### 1. 建立 Agent Loop

第一版可以采用固定流程：

```text
Start
→ UnderstandTask
→ InspectWorkspace
→ CreatePlan
→ ExecuteNextStep
→ ObserveResult
→ Validate
→ Finish
```

每一步都生成结构化记录。

#### 2. 支持基础工具

第一批工具建议只做最必要的：

```text
list_files
read_file
search_text
apply_patch
run_command
git_status
git_diff
```

不建议第一版就加入复杂的代码语义分析。

#### 3. 引入计划机制

计划可以是一个简单结构：

```text
Plan
- item id
- description
- status: pending / in_progress / completed / failed
- related files
- notes
```

Agent 每次执行前都应该知道：

- 当前目标是什么
- 哪个计划项正在执行
- 上一步结果是什么
- 是否需要调整计划

#### 4. 小步执行原则

第一版就要建立小步修改原则：

- 一次只处理一个明确子任务
- 修改前记录原因
- 修改后记录 diff
- 修改后尽量运行验证命令
- 不做无关重构

#### 5. 基础验证机制

支持让 Agent 根据项目类型选择验证方式，例如：

```text
npm test
mvn test
gradle test
pytest
go test
cargo test
```

验证命令可以先由 Agent 建议，再由用户确认或配置白名单。

### 阶段产出

- 可运行的单 Agent Loop
- 基础工具集合
- 简单任务计划结构
- 基础执行日志
- 文件 diff 记录
- 验证结果记录

### 验收标准

能完成如下任务：

```text
用户：帮我修复这个 Java 方法的 bug，并跑测试。
Agent：
1. 搜索相关代码
2. 阅读相关文件
3. 制定简短计划
4. 修改目标文件
5. 展示 diff
6. 运行测试
7. 汇报结果
```

## 阶段 2：可审计运行时

### 阶段目标

将第一阶段的执行过程系统化，形成真正的 Audit Runtime。

这一阶段的重点不是增强智能，而是增强可追踪性、可恢复性和安全性。

### 核心任务

#### 1. 建立事件流模型

每个关键动作都变成事件：

```text
TaskCreated
TaskUnderstood
PlanCreated
PlanUpdated
ToolCallRequested
ToolCallStarted
ToolCallCompleted
ToolCallFailed
FileRead
FilePatched
CommandExecuted
ValidationStarted
ValidationCompleted
ApprovalRequested
ApprovalGranted
ApprovalDenied
AgentFinished
AgentFailed
```

事件流是后续可视化、回放、调试的基础。

#### 2. 建立审计日志

审计日志要记录：

```text
event id
task id
step id
timestamp
actor
action type
input summary
output summary
related files
permission level
approval status
error message
```

对 LLM 调用也要记录：

```text
model
prompt version
input context summary
output summary
token usage
decision type
```

注意：不一定保存完整 prompt，但要能追踪关键决策来源。

#### 3. 加入审批机制

需要审批的行为包括：

```text
修改文件
删除文件
移动文件
执行 shell 命令
访问网络
git 写操作
大范围 patch
```

审批对象应该结构化：

```text
ApprovalRequest
- action
- reason
- risk level
- affected files
- preview
- suggested command
```

#### 4. 支持失败恢复

失败不应该只是报错退出。需要定义恢复策略：

```text
retry
replan
ask_user
rollback_step
skip_step
fail_task
```

例如：

```text
测试失败 → 分析失败原因 → 修改计划 → 再次修复
命令不存在 → 询问用户或尝试项目内文档
patch 冲突 → 重新读取文件 → 重新生成 patch
权限不足 → 请求审批
```

#### 5. 文件变更审计

每次修改文件都应该记录：

```text
修改原因
修改前摘要
修改后摘要
diff
关联计划项
验证结果
```

后续可以支持按步骤查看文件变化。

### 阶段产出

- 事件流系统
- 审计日志系统
- 权限与审批机制
- 失败恢复策略
- 文件变更记录
- Agent 执行报告

### 验收标准

完成一个任务后，用户可以看到：

```text
Agent 做了哪些步骤
每一步为什么发生
读了哪些文件
改了哪些文件
执行了哪些命令
哪些动作经过审批
最终验证是否通过
```

## 阶段 3：状态机与工作流内核

### 阶段目标

把固定的 Agent Loop 抽象成状态机和工作流内核，为后续可视化编排做准备。

这个阶段开始参考 LangGraph，但重点是服务你的编码 Agent，而不是做一个通用图引擎。

### 核心任务

#### 1. 定义节点模型

第一批节点类型：

```text
LLMNode
ToolNode
ConditionNode
ApprovalNode
ValidationNode
MemoryNode
LoopNode
FinishNode
FailNode
```

每个节点应该有：

```text
node id
node type
input mapping
output mapping
retry policy
timeout
permission requirement
audit config
next edges
```

#### 2. 定义边模型

边负责控制流程跳转：

```text
AlwaysEdge
ConditionEdge
OnSuccessEdge
OnFailureEdge
OnApprovalGrantedEdge
OnApprovalDeniedEdge
OnMaxRetryEdge
```

边的判断应该基于结构化状态，而不是纯文本。

#### 3. 定义 AgentState

AgentState 应该包括：

```text
task
plan
current step
messages
observations
tool results
file changes
validation results
approval records
memory references
errors
runtime metadata
```

#### 4. 引入循环控制

编码 Agent 必须支持循环，但要可控：

```text
最大循环次数
最大失败次数
最大工具调用次数
最大模型调用次数
最大执行时间
```

循环退出条件包括：

```text
任务完成
验证通过
需要用户输入
达到最大重试
发生不可恢复错误
```

#### 5. 工作流 DSL 草案

可以设计 JSON/YAML DSL 表达工作流：

```yaml
name: coding-agent
start: understand_task

nodes:
  understand_task:
    type: llm
    next: inspect_workspace

  inspect_workspace:
    type: tool
    tool: search_text
    next: create_plan

  create_plan:
    type: llm
    next: execute_step

  execute_step:
    type: llm
    next:
      condition: action.type
      cases:
        tool: call_tool
        finish: finish
        ask_user: ask_user
```

第一版 DSL 不必强大，但要能表达节点、边、条件、权限和重试。

### 阶段产出

- 状态机执行引擎
- 节点模型
- 边模型
- AgentState 结构
- 工作流 DSL 草案
- 默认 Coding Agent Workflow

### 验收标准

同一个 Runtime 可以运行不同 Agent 模式：

```text
只读 Review Agent
可修改 CodeEdit Agent
Debug Agent
Test Agent
Planning Agent
```

不同模式通过工作流和权限配置区分，而不是写死在代码里。

## 阶段 4：项目记忆与代码理解

### 阶段目标

让 Agent 从会读文件升级为逐步理解项目。

这个阶段引入 Memory、RAG 和代码分析，但仍然围绕编码任务服务。

### 核心任务

#### 1. 建立三层 Memory

短期记忆：

```text
当前任务
当前计划
当前观察
当前文件片段
当前错误
```

项目记忆：

```text
项目结构
技术栈
常用命令
测试方式
模块说明
代码规范
关键入口
```

经验记忆：

```text
用户偏好
历史任务经验
常见失败命令
项目特殊约束
不要修改的文件
```

#### 2. 项目扫描与索引

支持生成项目画像：

```text
语言
框架
包管理器
构建工具
测试框架
主要模块
入口文件
配置文件
文档文件
```

这份画像可以作为 Agent 每次任务的基础上下文。

#### 3. RAG 检索

RAG 不要只检索代码片段，还可以检索：

```text
项目文档
README
架构说明
历史任务摘要
常见命令
代码规范
错误处理记录
```

#### 4. 代码结构分析

先支持基础能力：

```text
文件 outline
类和方法列表
函数签名
import / dependency
简单调用关系
符号搜索
```

后续再增加更重的语义分析。

#### 5. 任务结束后的记忆沉淀

每次任务完成后，Agent 可以生成：

```text
这次任务修改了什么
验证方式是什么
发现了什么项目规则
哪些信息对未来任务有用
```

但写入长期记忆前应允许用户审批。

### 阶段产出

- 项目画像
- 短期记忆管理
- 项目记忆库
- RAG 检索机制
- 基础代码结构分析
- 任务总结与记忆沉淀机制

### 验收标准

Agent 能回答：

```text
这个项目怎么运行测试？
主要模块有哪些？
某个功能大概在哪些文件？
上次修复类似问题时做了什么？
哪些文件通常不应该随便改？
```

## 阶段 5：可视化编排与回放

### 阶段目标

把前面阶段积累的状态机、事件流和审计日志可视化。

这个阶段的重点是先做执行可视化和任务回放，再做拖拽编排。

### 核心任务

#### 1. 执行过程可视化

展示：

```text
当前任务
当前计划
当前节点
节点执行历史
工具调用历史
文件变更
验证结果
审批记录
错误与重试
```

用户应该能看到 Agent 正在做什么，而不是只看到一段聊天文本。

#### 2. 工作流图可视化

展示节点和边：

```text
哪个节点已执行
哪个节点正在执行
哪条边被选择
为什么选择这条边
哪些节点失败过
哪些节点重试过
```

#### 3. 文件变更视图

提供按步骤查看 diff 的能力：

```text
按任务查看 diff
按计划项查看 diff
按节点查看 diff
按文件查看 diff
```

#### 4. 审批 UI

审批界面应该展示：

```text
动作类型
风险等级
影响范围
执行原因
命令内容或 patch 预览
允许 / 拒绝 / 修改后允许
```

#### 5. 任务回放

任务完成后可以回放：

```text
从用户请求开始
每次模型决策
每次工具调用
每次状态变化
每次文件修改
最终验证结果
```

回放能力是可审计 Agent 的重要差异化能力。

#### 6. 可视化编排

最后再支持拖拽配置：

```text
添加节点
连接边
配置条件
配置工具
配置权限
配置重试
配置模型
配置终止条件
```

第一版可视化编排不需要覆盖所有高级能力，重点是能编辑常见 Agent Workflow。

### 阶段产出

- Agent 执行仪表盘
- 工作流图展示
- 审计时间线
- 文件 diff 视图
- 审批界面
- 任务回放
- 基础可视化工作流编辑器

### 验收标准

用户可以直观看到：

```text
Agent 当前执行到哪一步
为什么进入这个节点
它准备修改什么
哪些动作需要我批准
任务完成后完整过程如何回放
```

## 阶段 6：多 Agent、插件化与产品化

### 阶段目标

在单 Agent 稳定后，再扩展为多 Agent 协作、插件生态和产品级体验。

这个阶段不要太早开始，否则容易把系统复杂度拉爆。

### 核心任务

#### 1. 多 Agent 模式

内置不同 Agent：

```text
PlanningAgent
CodeEditAgent
ReviewAgent
DebugAgent
TestAgent
ResearchAgent
RefactorAgent
```

它们共享 Runtime，但有不同：

```text
工具权限
系统提示词
工作流
记忆访问范围
终止条件
审批策略
```

#### 2. Agent 协作协议

定义 Agent 之间如何协作：

```text
主 Agent 分配任务
子 Agent 返回结构化结果
子 Agent 不能直接越权操作
所有子任务也进入审计日志
```

例如：

```text
主 Agent：负责整体任务
Explorer Agent：只读代码，返回发现
Editor Agent：负责修改文件
Verifier Agent：负责运行测试和分析失败
Reviewer Agent：负责检查风险
```

#### 3. 插件系统

工具层可以插件化：

```text
文件插件
Shell 插件
Git 插件
Maven 插件
Gradle 插件
NPM 插件
Docker 插件
Database 插件
HTTP API 插件
IDE 插件
```

插件需要声明：

```text
工具列表
权限等级
输入输出 schema
审计字段
风险说明
```

#### 4. 团队协作能力

如果未来面向团队，可以加入：

```text
任务共享
审计报告导出
策略配置
权限模板
项目级记忆
团队级记忆
执行记录归档
```

#### 5. 产品化能力

包括：

```text
任务历史
执行报告
工作流模板
Agent 模板
权限策略模板
失败诊断
成本统计
模型调用统计
```

### 阶段产出

- 多 Agent Runtime
- 子 Agent 调度机制
- 插件系统
- Agent 模板
- 工作流模板
- 团队审计报告
- 产品级管理界面

### 验收标准

系统可以支持：

```text
一个复杂编码任务被拆分给多个 Agent
所有子任务都可审计
所有工具调用都有权限控制
团队可以复用工作流和 Agent 模板
```

## 推荐 MVP 范围

如果要真正开始落地，建议第一版只做这些：

```text
单 Agent Loop
基础文件工具
基础命令工具
Git diff/status
结构化计划
事件日志
文件 patch 记录
简单审批
简单验证
任务结束报告
```

暂时不要做：

```text
复杂可视化拖拽
多 Agent
完整代码语义分析
复杂 RAG
插件市场
云端协作
```

第一版最重要的是证明：

> Agent 能在一个真实代码库里完成小型修改，并且整个过程可追踪、可解释、可回放。

## 关键里程碑

### Milestone 1：能完成任务

Agent 可以读代码、改代码、跑验证。

### Milestone 2：能解释过程

Agent 可以说明每一步为什么做。

### Milestone 3：能审计变更

用户可以查看每个文件修改的来源、原因和 diff。

### Milestone 4：能失败恢复

测试失败、patch 冲突、命令失败时，Agent 不直接崩溃，而是重新规划或请求用户介入。

### Milestone 5：能配置流程

不同 Agent 模式可以通过工作流配置表达。

### Milestone 6：能可视化回放

用户可以从 UI 中完整回看一次任务的执行路径。

## 最终产品形态

长期来看，可以把它设计成：

```text
一个面向开发者的可审计 Agent Runtime
+
一个可视化工作流编排器
+
一组编码工具插件
+
一套项目记忆系统
+
一套 Agent 执行审计系统
```

它的差异化不是更聪明地聊天，而是：

```text
更可靠地执行
更清楚地解释
更安全地修改
更完整地记录
更容易被团队信任
```

这会比单纯做一个 LangChain4j Agent 更有长期价值。
