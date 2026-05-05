# 第4阶段 代码理解

第4阶段代码理解设计上轻量级且本地化。它提供足够的结构用于规划、审查和检索，无需语言服务器。

## 扫描器输入

`ProjectScanner` 通过工作区边界保护行走工作区，并跳过常见的生成或编辑器目录：

```text
.git
target
node_modules
.idea
.vscode
build
out
```

默认扫描预算：

```text
最大文件数：2000
每个文件最大字节数：256 KB
最大总字节数：10 MB
```

扫描器使用 `FileClassifier` 对文件进行分类：

```text
SOURCE
TEST
DOCS
CONFIG
BUILD_FILE
MIGRATION
OTHER
```

单个文件的扫描错误被计为跳过的工作。除非根扫描设置失败，否则不会中止整个扫描。

## 项目配置生成器

`ProjectProfileBuilder` 将扫描观察转化为工作区级别的配置文件。

当前检测规则包括：

```text
pom.xml -> Maven
src/main/java -> Java 源代码布局
src/test/java -> Java 测试布局和 JUnit 候选
org.springframework.boot 在构建/源内容中 -> Spring Boot
src/main/resources/db/migration -> Flyway
README.md 和 docs/** -> 文档路径
```

配置文件存储语言、框架、构建工具、测试工具、包管理器、入口点、重要路径、文档路径、配置路径、置信度和上次扫描运行ID。

## 文档索引

`DocumentIndexer` 从 README、文档、配置、构建文件和迁移中索引项目文本。`TaskReportIndexer` 将历史任务报告索引为虚拟路径：

```text
task-reports/{taskId}/{reportId}.md
```

块保留：

```text
path
document_type
title
chunk_index
content
content_hash
line_start
line_end
token_count
scan_run_id
```

重复块通过唯一内容约束被忽略。

## Java 符号提取

`JavaSymbolExtractor` 是一个尽力而为的正则表达式大纲提取器。它检测：

```text
class
interface
enum
record
constructor
method
field
constant
```

每个符号存储：

```text
path
language
symbol_type
symbol_name
container_name
signature
line_start
line_end
visibility
scan_run_id
```

提取器设计上保守。它支持常见的 Java 声明，但不是完整解析器。不支持的语法应减少符号覆盖率，不应导致扫描失败。

## 符号 API

按可选名称和类型搜索：

```text
GET /api/workspaces/{workspaceId}/symbols?query=WorkflowAgentExecutor&type=CLASS
```

获取单个文件大纲：

```text
GET /api/workspaces/{workspaceId}/outline?path=src/main/java/com/nask/agent/workflow/WorkflowAgentExecutor.java
```

等效的 CLI 命令：

```powershell
agent symbols <workspaceId> --query WorkflowAgentExecutor
agent outline <workspaceId> --path src/main/java/com/nask/agent/workflow/WorkflowAgentExecutor.java
```

## 检索使用

`MemorySearchService` 搜索代码符号以及索引文档和内存项。符号匹配在以下情况下评分很高：

```text
symbol_name 精确匹配查询令牌
symbol_name 部分匹配查询令牌
container_name 匹配
path 或 signature 匹配
```

`CODE_UNDERSTANDING` 消费检索的符号结果，并在工作流元数据中显示相关代码文件路径。后续的规划和操作提示通过 `AgentState` 接收相同的 `MemoryContext`。

## 已知限制

```text
无语言服务器集成
无跨文件调用图
无 Java 类型解析
无语义重命名或引用索引
正则表达式提取可能会漏掉多行或不常见的声明
```

这些限制对第4阶段是可以接受的，因为所有结果被视为源引用的提示，而非权威编译器事实。
