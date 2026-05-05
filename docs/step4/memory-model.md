# 第4阶段 内存模型

第4阶段将项目理解存储为可审计的运行时事实。它不将模型聊天历史保存为长期内存。

## 表

### project_profile

由有界扫描生成的工作区级项目摘要。

关键字段：

```text
workspace_id
language_summary
frameworks_json
build_tools_json
test_tools_json
entrypoints_json
important_paths_json
docs_paths_json
config_paths_json
last_scan_run_id
confidence
```

当前扫描器检测 Java、Maven、Spring Boot、Flyway、JUnit、文档、配置和入口点提示。

### project_scan_run

每次扫描的持久记录。

关键字段：

```text
workspace_id
task_id
run_id
status
scan_reason
files_seen
files_indexed
files_skipped
summary
metadata_json
```

扫描受以下限制：

```text
agent.project-scan.max-files
agent.project-scan.max-file-bytes
agent.project-scan.max-total-bytes
```

忽略的目录包括 `.git`、`target`、`node_modules`、`.idea`、`.vscode`、`build` 和 `out`。

### indexed_document

来自 README、文档、配置/构建文件、迁移和历史任务报告的可搜索块。

文档类型：

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

每个块保留 `path`、`line_start`、`line_end`、`content_hash` 和 `scan_run_id`。

### code_symbol

尽力而为的 Java 大纲索引。

符号类型：

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

每个符号保留源路径、签名、容器名称、可见性和行范围。

### project_memory_item

长生命周期可重用内存。用户编写的内存可以直接创建；任务衍生内存仅在批准后才写入。

内存类型：

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

状态：

```text
PROPOSED
APPROVED
REJECTED
ARCHIVED
SUPERSEDED
```

搜索服务仅返回 `APPROVED` 和未过期的 `PROPOSED` 项。

### memory_retrieval

记录 API 调用或工作流节点使用的每个上下文检索。

关键字段：

```text
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

结果引用保留源类型、源ID、路径、行范围、符号名称、扫描运行ID、任务ID和运行ID（如果可用）。

### memory_write_proposal

任务衍生的长期内存建议，等待用户批准。

关键字段：

```text
workspace_id
task_id
run_id
proposal_type
title
content
source_refs_json
status
approval_request_id
project_memory_item_id
```

批准请求类型是 `MEMORY_WRITE`。批准提议会写入 `APPROVED` 的 `project_memory_item`；拒绝它会将提议标记为 `REJECTED`，不会导致已完成的运行失败。

## 检索

`MemorySearchService` 组合三个源：

```text
project_memory_item
indexed_document
code_symbol
```

支持的过滤器：

```text
memoryType
documentType
symbolType
limit
```

第一版本排名信号：

```text
标题/内容/路径/签名中的关键字命中数
文档类型优先级
符号名称精确或部分匹配
内存状态和置信度
新近性
```

无需外部向量数据库或嵌入服务。`EmbeddingProvider` 仅作为可选的未来扩展点存在。

## 工作流使用

默认编码工作流使用：

```text
PROJECT_SCAN
PROJECT_MEMORY
CODE_UNDERSTANDING
TASK_SUMMARY_MEMORY
```

`PROJECT_MEMORY` 在创建计划前检索 `MemoryContext`。LLM 提示收到一个紧凑汇总，包含检索ID、配置文件汇总、排名结果和源引用。

`TASK_SUMMARY_MEMORY` 在验证后创建 `TASK_LESSON` 提议。提议在成为长期内存前需要明确批准。
