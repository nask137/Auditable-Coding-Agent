# 阶段 4 工作计划：项目记忆与代码理解

## 1. 进度确认结论

当前项目已经具备进入阶段 4 的基础。

阶段 1 到阶段 3 已经落地的核心能力包括：

```text
Workspace / Task / AgentRun
AgentStep / AgentAction
Plan / PlanItem
基础文件工具和 Git 只读工具
命令策略、权限判断和审批
AuditEvent / RuntimeFailure / UserInputRequest
恢复预算和失败恢复路径
ValidationResult / TaskReport
REST API / 轻量 CLI
Stub LLM 和 HTTP LLM Gateway
WorkflowDefinition / WorkflowNodeExecution / WorkflowEdgeDecision
WorkflowAgentExecutor
AgentStateAssembler
coding-agent / review-agent / test-agent 默认工作流
```

阶段 3 已经把固定 Loop 抽象为工作流内核，并在默认工作流中预留了：

```text
PROJECT_MEMORY
CODE_UNDERSTANDING
```

当前这两个节点还主要从运行时事实和恢复 notes 中装配上下文，尚未形成持久化项目画像、索引、检索和任务经验沉淀。

阶段 4 的目标是把 Agent 从“能读文件”升级为“能逐步理解项目”，并让这种理解以可审计、可审批、可复用的方式接入工作流。

## 2. 阶段目标

阶段 4 的目标是建立项目记忆和代码理解基础设施，让 Runtime 可以在任务开始前和执行过程中提供稳定的项目上下文。

核心判断标准：

```text
Agent 可以基于持久化项目画像、文档索引、代码结构和历史任务摘要做计划，
而不是每次都从零开始列目录、搜索文本和猜测项目结构。
```

本阶段完成后，应能回答：

```text
这个项目使用什么语言、框架、构建工具和测试方式？
主要模块和入口在哪里？
某个符号或功能大概在哪些文件？
README、设计文档和历史任务摘要中有哪些相关信息？
最近类似任务修改过什么、如何验证、踩过什么坑？
哪些文件、命令或项目约束需要未来任务注意？
```

## 3. 阶段边界

### 3.1 本阶段要做

```text
ProjectProfile 项目画像
ProjectScanRun 扫描记录
ProjectMemoryItem 项目记忆条目
CodeSymbol / CodeOutline 基础代码结构索引
文档和代码片段索引
关键词检索和可选 embedding 检索接口
MemoryRetrieveNode
ProjectScanNode
CodeUnderstandingNode 增强
TaskSummaryMemoryNode
记忆写入审批
AgentState 增加 memory / code context
LLM prompt 接入项目上下文
API / CLI 查询项目画像、记忆和符号
阶段 4 单元测试、集成测试和 CLI 手工测试文档
```

### 3.2 本阶段暂不做

```text
全量语言服务器集成
跨仓库知识库
复杂语义代码图谱
自动重构建议引擎
向量数据库运维组件
多用户记忆隔离
团队共享记忆权限模型
Web 控制台
可视化代码地图
自动写入长期记忆且无需审批
```

说明：阶段 4 先建立可落地、可审计的本地项目记忆。复杂 RAG、代码图谱和可视化留到后续阶段演进。

## 4. 设计原则

### 4.1 记忆是 Runtime 事实，不是模型聊天记录

项目记忆必须结构化持久化，并能关联来源。

每条记忆至少应能回答：

```text
来自哪个 workspace
由哪个扫描、任务或用户输入产生
内容类型是什么
可信度和新鲜度如何
是否经过用户审批
未来任务是否允许使用
```

不要把长期记忆只塞进 prompt 或大 JSON 中。

### 4.2 检索结果必须可追溯

无论来自代码、文档还是历史任务，检索结果都要保留 source reference：

```text
file path
line range
symbol name
task id
run id
report id
memory item id
scan run id
```

后续报告和审计事件要能说明：Agent 为什么知道这件事。

### 4.3 先做本地确定性索引，再接入可选向量检索

第一版优先支持：

```text
文件路径和文件类型分类
README / docs / 配置文件提取
关键词和模糊搜索
Java 基础 symbol / outline
历史任务摘要检索
```

embedding 检索可以通过接口预留，但不要把阶段 4 阻塞在外部向量库或模型 embedding 服务上。

### 4.4 长期记忆写入默认需要审批

任务结束后 Agent 可以提出“这条信息未来有用”，但写入长期记忆前应产生审批请求。

可以无需审批写入的内容应限制在低风险、可重建的扫描事实，例如：

```text
项目语言
构建工具
检测到的配置文件
代码符号索引
```

用户偏好、项目约束、不要修改的文件、历史经验等应走审批。

### 4.5 阶段 4 能力通过工作流节点接入

不要把记忆逻辑直接散落到 `WorkflowAgentExecutor` 中。

建议新增或增强节点：

```text
ProjectScanNode
MemoryRetrieveNode
CodeUnderstandingNode
TaskSummaryMemoryNode
```

这些节点产出的 context 进入 `AgentState` 和 LLM prompt，执行路径仍由 workflow node / edge 记录审计。

## 5. 核心模型

### 5.1 ProjectProfile

项目画像是 workspace 级别的稳定摘要。

建议字段：

```text
id
workspace_id
language_summary
frameworks_json
build_tools_json
test_tools_json
package_managers_json
entrypoints_json
important_paths_json
docs_paths_json
config_paths_json
last_scan_run_id
confidence
created_at
updated_at
```

画像由扫描器生成，也可以被用户修正。用户修正应记录 AuditEvent。

### 5.2 ProjectScanRun

每次扫描都要成为可审计事实。

建议字段：

```text
id
workspace_id
task_id
run_id
status
scan_reason
started_at
completed_at
files_seen
files_indexed
files_skipped
summary
metadata_json
```

扫描应受预算限制：

```text
max files
max bytes per file
max total bytes
ignored directories
allowed file extensions
```

### 5.3 ProjectMemoryItem

项目记忆条目用于保存项目规则、经验和任务沉淀。

建议字段：

```text
id
workspace_id
memory_type
scope
title
content
source_type
source_id
source_path
source_line_start
source_line_end
status
confidence
expires_at
created_by
created_at
approved_by
approved_at
metadata_json
```

第一批 memory_type：

```text
PROJECT_RULE
TECH_STACK
COMMON_COMMAND
TEST_STRATEGY
MODULE_SUMMARY
ENTRYPOINT
USER_PREFERENCE
TASK_LESSON
FAILURE_PATTERN
DO_NOT_TOUCH
```

第一批 status：

```text
PROPOSED
APPROVED
REJECTED
ARCHIVED
SUPERSEDED
```

### 5.4 CodeSymbol

基础代码结构索引。

建议字段：

```text
id
workspace_id
scan_run_id
path
language
symbol_type
symbol_name
container_name
signature
line_start
line_end
visibility
metadata_json
```

第一批 symbol_type：

```text
CLASS
INTERFACE
ENUM
RECORD
METHOD
CONSTRUCTOR
FIELD
FUNCTION
CONSTANT
```

Java 项目第一版可以基于正则和轻量 parser 生成 outline，后续再替换为 JavaParser 或语言服务器。

### 5.5 IndexedDocument

文档和代码片段索引。

建议字段：

```text
id
workspace_id
scan_run_id
path
document_type
title
chunk_index
content
content_hash
line_start
line_end
token_count
metadata_json
```

第一批 document_type：

```text
README
DOCS
CONFIG
SOURCE
TEST
MIGRATION
BUILD_FILE
TASK_REPORT
MEMORY
```

### 5.6 MemoryContext

执行时传给 AgentState / LLM prompt 的聚合结果。

建议包含：

```text
project profile summary
relevant memory items
relevant docs
relevant symbols
relevant recent task summaries
common commands
do-not-touch warnings
source references
```

MemoryContext 不必完整持久化为事实源，但每次检索应记录：

```text
query
filters
selected result ids
selection reason
```

## 6. 数据库建议

新增迁移建议命名：

```text
V4__phase4_project_memory.sql
```

建议新增表：

```text
project_profile
project_scan_run
project_memory_item
code_symbol
indexed_document
memory_retrieval
memory_write_proposal
```

### 6.1 memory_retrieval

用于记录一次任务中检索过什么上下文。

关键字段：

```text
id
workspace_id
task_id
run_id
workflow_node_execution_id
query_text
filters_json
result_refs_json
summary
created_at
```

### 6.2 memory_write_proposal

用于保存任务结束后待审批的记忆写入建议。

关键字段：

```text
id
workspace_id
task_id
run_id
proposal_type
title
content
source_refs_json
status
approval_request_id
created_at
resolved_at
```

审批通过后转换为 `project_memory_item`。

## 7. 工作流接入设计

### 7.1 默认 Coding Workflow

阶段 4 后建议默认 coding-agent 路径：

```text
TASK_UNDERSTANDING
WORKSPACE_INSPECTION
PROJECT_MEMORY
CODE_UNDERSTANDING
PLAN_CREATION
PLAN_ITEM_EXECUTION
VALIDATION
TASK_SUMMARY_MEMORY
REPORT
FINISH
```

其中：

```text
PROJECT_MEMORY：检索项目画像、记忆、文档和历史任务摘要
CODE_UNDERSTANDING：基于任务和 search hints 检索符号、outline 和相关文件
TASK_SUMMARY_MEMORY：任务结束后提出可沉淀的记忆建议
```

### 7.2 Review Workflow

Review Workflow 应保持只读。

允许：

```text
读取 project profile
读取 memory item
读取 indexed document
读取 code symbol
生成 review report
```

禁止：

```text
写文件
执行 shell 命令
自动写入长期记忆
```

### 7.3 Test Workflow

Test Workflow 可使用项目记忆寻找测试命令。

优先级：

```text
APPROVED COMMON_COMMAND memory
ProjectProfile test_tools
README / docs 中的测试章节
构建文件推断
LLM 建议
```

命令执行仍受 `CommandPolicyService` 和审批控制。

## 8. Milestone 拆分

### Milestone 1：领域模型和 schema

目标：

```text
先把项目画像、扫描记录、记忆条目、符号和文档索引作为持久化事实建立起来。
```

交付内容：

```text
新增 V4 migration
新增 ProjectProfile / ProjectScanRun / ProjectMemoryItem
新增 CodeSymbol / IndexedDocument
新增 MemoryRetrieval / MemoryWriteProposal
新增 repository 和基础 service
补充 Domain 枚举和 AuditEventType
```

验收标准：

```text
可以为 workspace 保存项目画像
可以记录一次扫描运行
可以保存和查询 APPROVED / PROPOSED 记忆
可以保存代码符号和文档 chunk
所有记录能关联 workspaceId，必要时关联 taskId / runId
```

### Milestone 2：项目扫描器

目标：

```text
自动生成可复用的项目画像和基础索引。
```

交付内容：

```text
ProjectScanner
ProjectProfileBuilder
FileClassifier
IgnoreRules
配置文件识别
README / docs 路径识别
构建工具和测试工具识别
扫描预算配置
扫描审计事件
```

第一批识别规则：

```text
pom.xml -> Maven / Java / Spring Boot
build.gradle / settings.gradle -> Gradle
package.json -> Node package manager and scripts
src/main/java -> Java source layout
src/test/java -> JUnit style test layout
db/migration -> Flyway migrations
README.md / docs/** -> project docs
```

验收标准：

```text
扫描当前项目能识别 Java、Spring Boot、Maven、Flyway、JUnit、docs 路径
扫描受 max files / bytes 限制
扫描跳过 target、.git、node_modules 等目录
扫描结果写入 project_scan_run 和 project_profile
```

### Milestone 3：文档和任务摘要索引

目标：

```text
把项目文档和历史任务报告变成可检索上下文。
```

交付内容：

```text
DocumentIndexer
Markdown / text chunker
Config file summarizer
TaskReportIndexer
IndexedDocumentService
content hash 去重
```

验收标准：

```text
README、docs 和配置文件会被切分成 chunk
chunk 保留 path 和 line range
同一内容重复扫描不会重复写入
已完成任务报告可作为 TASK_REPORT chunk 被检索
```

### Milestone 4：代码 outline 和符号搜索

目标：

```text
提供基础代码结构理解能力，先覆盖 Java 项目。
```

交付内容：

```text
CodeOutlineExtractor
JavaSymbolExtractor
CodeSymbolService
SymbolSearchService
按 path / name / type 搜索
按任务关键词推荐相关符号
```

验收标准：

```text
可以列出 Java class / record / enum / interface
可以列出方法和构造器签名
可以按符号名搜索对应文件和行号
可以查询某个文件的 outline
失败或无法解析文件时记录 skipped，不阻断整个扫描
```

### Milestone 5：检索服务

目标：

```text
为工作流节点和 API 提供统一 Memory Retrieval。
```

交付内容：

```text
MemoryQuery
MemorySearchService
ProjectContextRetriever
关键词检索
按 memory type / document type / symbol type 过滤
结果打分和去重
MemoryContext 组装
memory_retrieval 记录
可选 EmbeddingProvider 接口
```

第一版排序信号：

```text
任务关键词命中
文件路径相似度
memory status 和 confidence
最近更新时间
文档类型优先级
symbol name 精确匹配
```

验收标准：

```text
给定 task request 可以返回项目画像、相关文档、相关符号和记忆条目
每次检索有 memory_retrieval 记录
检索结果包含 source refs
不依赖外部向量库也能工作
```

### Milestone 6：工作流节点接入

目标：

```text
让项目记忆和代码理解成为工作流内核中的一等节点能力。
```

交付内容：

```text
ProjectScanNodeExecutor
MemoryRetrieveNodeExecutor
CodeUnderstandingNodeExecutor 增强
TaskSummaryMemoryNodeExecutor
AgentState 增加 MemoryContext
LlmPromptFactory 接入 MemoryContext
内置 workflow 更新
```

验收标准：

```text
coding-agent 在创建计划前会检索项目上下文
review-agent 可以基于项目画像和符号索引生成报告
test-agent 会优先使用项目记忆中的测试命令
工作流节点执行和边选择仍完整记录
```

### Milestone 7：记忆写入审批

目标：

```text
任务结束后可以沉淀经验，但长期记忆写入必须可控。
```

交付内容：

```text
MemoryWriteProposalService
TaskSummaryMemory prompt
ApprovalRequest 集成
审批通过后写入 ProjectMemoryItem
审批拒绝后标记 REJECTED
记忆归档和替换接口
```

验收标准：

```text
任务完成后可生成 TASK_LESSON / COMMON_COMMAND / PROJECT_RULE 建议
建议默认处于 PROPOSED 或 WAITING_APPROVAL
用户批准后才成为 APPROVED memory
报告中说明产生了哪些记忆建议和审批状态
```

### Milestone 8：API / CLI 观察能力

目标：

```text
用户可以查看、触发和审计项目记忆能力。
```

建议 API：

```text
POST /api/workspaces/{workspaceId}/scan
GET  /api/workspaces/{workspaceId}/profile
GET  /api/workspaces/{workspaceId}/scan-runs
GET  /api/workspaces/{workspaceId}/memory
POST /api/workspaces/{workspaceId}/memory
POST /api/memory-proposals/{proposalId}/approve
POST /api/memory-proposals/{proposalId}/reject
GET  /api/workspaces/{workspaceId}/symbols
GET  /api/workspaces/{workspaceId}/outline?path=...
GET  /api/workspaces/{workspaceId}/search-context?q=...
```

建议 CLI：

```powershell
agent scan <workspaceId>
agent profile <workspaceId>
agent memory <workspaceId>
agent remember <workspaceId> --type PROJECT_RULE --title "..." --content "..."
agent memory-approve <proposalId>
agent memory-reject <proposalId>
agent symbols <workspaceId> --query "WorkflowAgentExecutor"
agent outline <workspaceId> --path src/main/java/...
agent context <workspaceId> --query "how to run tests"
```

验收标准：

```text
可以手动触发扫描
可以查询项目画像
可以查询和维护记忆条目
可以查询符号和文件 outline
可以看到某次任务检索了哪些上下文
```

### Milestone 9：报告和文档

目标：

```text
阶段 4 能力可以被手工验证，并且后续阶段能复用。
```

交付内容：

```text
TaskReport 增加 Project Context section
README 更新项目记忆配置和 CLI 使用方式
docs/step4/phase4-cli-test-guide.md
docs/step4/memory-model.md
docs/step4/code-understanding.md
```

验收标准：

```text
报告中包含使用了哪些项目记忆、文档、符号和历史任务摘要
文档能指导用户扫描项目、查询记忆、运行 coding/review/test 工作流
```

## 9. 建议实施顺序

```text
1. 新增 phase4 schema、领域模型和 repository
2. 实现 ProjectScanner 和 ProjectProfileBuilder
3. 实现 DocumentIndexer，把 README、docs、配置和历史报告写入 indexed_document
4. 实现 Java CodeOutlineExtractor 和 CodeSymbolService
5. 实现 MemorySearchService 和 ProjectContextRetriever
6. 增强 AgentStateAssembler，装配 MemoryContext
7. 增强 PROJECT_MEMORY / CODE_UNDERSTANDING 节点
8. 更新 LLM prompt，把检索结果作为有来源的上下文传入
9. 增加 TaskSummaryMemoryNode 和记忆写入审批
10. 增加 API / CLI 查询和维护能力
11. 扩展报告、README 和阶段 4 CLI 测试文档
12. 补齐单元测试和集成测试
```

推荐先让扫描和关键词检索稳定工作，再考虑 embedding。这样阶段 4 可以独立于外部模型能力完成闭环。

## 10. 测试计划

### 10.1 单元测试

重点覆盖：

```text
ProjectProfileBuilder
ProjectScanner ignore rules 和预算控制
FileClassifier
DocumentIndexer chunking 和去重
JavaSymbolExtractor
SymbolSearchService
MemorySearchService
ProjectContextRetriever
MemoryWriteProposalService
AgentStateAssembler memory context 装配
```

典型测试用例：

```text
pom.xml 被识别为 Maven 项目
Spring Boot application class 被识别为入口
target / .git / node_modules 被跳过
README chunk 保留 line range
重复 content hash 不重复写入
Java record / enum / interface / method 可被提取
symbol 精确匹配优先于模糊匹配
APPROVED memory 优先于 PROPOSED memory
DO_NOT_TOUCH memory 会进入 warning context
```

### 10.2 集成测试

重点覆盖：

```text
扫描当前 Java Spring Boot 项目并生成项目画像
coding-agent 创建计划前使用 MemoryContext
review-agent 基于 symbol 和 docs 生成报告且不写文件
test-agent 从项目画像或记忆中选择 Maven test 命令
任务完成后生成 memory proposal
审批 memory proposal 后写入 project_memory_item
报告包含 project context 来源引用
```

### 10.3 手工 CLI 测试

准备场景：

```text
扫描当前 workspace
查询项目画像
搜索 WorkflowAgentExecutor 符号
查询“怎么运行测试”
手动新增一个 COMMON_COMMAND memory
运行 test-agent，确认优先使用该 memory
运行 coding-agent，确认计划上下文包含相关 docs 和 symbol
任务完成后审批记忆建议
```

检查：

```text
agent scan
agent profile
agent symbols
agent context
agent memory
agent workflow-path
agent report
```

## 11. 阶段 4 验收标准

阶段 4 完成后，应满足：

```text
workspace 可以生成和查询项目画像
项目扫描受预算和忽略规则控制
README、docs、配置文件和历史报告可以被索引和检索
Java 代码可以生成基础 outline 和 symbol index
AgentState 可以装配 MemoryContext
coding/review/test workflow 可以使用项目记忆和代码理解节点
检索结果保留 source refs 并写入审计记录
任务结束后可以提出记忆写入建议
长期记忆写入默认需要用户审批
报告包含项目上下文来源摘要
API / CLI 可以扫描、查询、搜索和维护记忆
```

## 12. 风险和约束

主要风险：

```text
扫描范围过大导致任务启动变慢
记忆写入不加审批会污染后续任务上下文
检索结果缺少来源会削弱可审计性
过早接入复杂向量库会增加部署成本
正则级 symbol 提取无法覆盖所有 Java 语法
历史任务经验过期后仍被高优先级使用
```

约束建议：

```text
扫描默认增量或按需触发，任务启动只做轻量 freshness check
所有长期记忆有 status、confidence 和 source refs
检索上下文要限制数量和 token 预算
低置信度或过期记忆不得直接作为强约束
代码 outline 第一版允许 best effort，解析失败不阻断任务
所有文件读取仍通过 WorkspacePathGuard
所有命令建议仍通过 CommandPolicy 和审批
```

## 13. 完成后的下一阶段入口

阶段 4 完成后，进入阶段 5 的条件是：

```text
项目画像、记忆、索引、检索和使用路径都可查询
工作流节点能说明自己使用了哪些上下文
报告能展示项目上下文和来源引用
任务历史可以沉淀为经审批的长期记忆
代码符号和文档索引能支持基础项目理解
```

具备这些条件后，阶段 5 的可视化编排与回放可以直接展示：

```text
工作流节点路径
每个节点使用的项目记忆
检索到的文档和代码符号
任务结束后产生的记忆建议
审批、恢复、验证和报告之间的完整关系
```
