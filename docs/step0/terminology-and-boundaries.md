# 统一术语与边界定义

## 1. 文档目标

本文档用于统一阶段 0 中反复出现的核心术语、权限边界和 MVP 级别能力声明。

如果其他阶段 0 文档存在表述差异，以本文档为准。后续实现设计应优先遵守本文档中的定义。

## 2. 核心术语

| 术语 | 定义 |
| --- | --- |
| Task | 用户提出的目标。Task 描述“要完成什么”，不代表一次具体执行。 |
| AgentRun | 对某个 Task 的一次执行尝试。一个 Task 可以有多次 AgentRun。 |
| AgentStep | Agent Loop 的一次迭代，表示运行时推进任务的一步。 |
| AgentAction | 一次原子动作意图，例如读取文件、申请执行命令、应用 patch、请求审批。 |
| ToolCall | 工具调用请求和执行记录，表示 Runtime 实际调用某个工具。 |
| FileChange | 实际发生的文件变更事实，包括创建、修改、删除、移动。 |
| CommandExecution | 命令申请和执行事实，包括命令内容、策略判断、审批、执行结果。 |
| ApprovalRequest | Runtime 暂停点，用于等待用户对高风险动作做出决策。 |
| AuditEvent | 不可变事件记录，用于任务追踪、回放和审计。 |
| PermissionDecision | Runtime 对某个 AgentAction 的权限裁决，结果为允许、需要审批或阻止。 |

## 3. 粒度边界

### 3.1 Task 与 AgentRun

`Task` 是用户目标，`AgentRun` 是一次执行尝试。

示例：

```text
Task：给 UserService 添加单元测试
AgentRun 1：第一次执行，测试失败
AgentRun 2：重新执行，测试通过
```

### 3.2 AgentStep 与 AgentAction

`AgentStep` 表示 Agent Loop 的一次迭代。

`AgentAction` 表示 Step 内的一个原子动作意图。

一个 `AgentStep` 可以包含多个 `AgentAction`。

示例：

```text
AgentStep：执行计划项“补充单元测试”
AgentAction 1：读取 UserService.java
AgentAction 2：读取 UserServiceTest.java
AgentAction 3：应用测试文件 patch
AgentAction 4：申请执行 mvn test
```

### 3.3 AgentAction 与 ToolCall

一个 `AgentAction` 是一个原子意图。

一个 `AgentAction` 最多绑定一个实际执行对象，例如：

```text
ToolCall
FileChange
CommandExecution
ApprovalRequest
```

如果一次 Step 需要多个工具调用，应拆成多个 AgentAction，而不是让一个 Action 承载多个执行事实。

这样做的好处：

- 审计更清楚
- 权限裁决更明确
- 审批可以绑定到单个原子动作
- 失败恢复更容易定位

## 4. Workspace 边界

MVP 中，workspace 是强安全边界。

统一规则：

```text
第一版 workspace 外路径一律 BLOCK，不进入普通审批流程。
```

也就是说：

- Agent 不能读取 workspace 外文件
- Agent 不能创建 workspace 外文件
- Agent 不能修改 workspace 外文件
- Agent 不能删除 workspace 外文件
- Agent 不能将文件移动到 workspace 外
- Agent 不能从 workspace 外移动文件到 workspace 内

如果用户确实希望 Agent 访问新的目录，应通过显式操作扩大 workspace 边界，而不是审批某次越界访问。

推荐后续机制：

```text
用户手动添加新的 Workspace
或
用户手动调整 Workspace rootPath / allowedPaths
```

这种操作本身需要记录审计事件。

## 5. 敏感文件边界

敏感文件分为两类：

```text
可审批敏感文件
强阻止敏感文件
```

MVP 统一规则：

- 敏感文件读取：默认 `REQUIRE_APPROVAL`
- 敏感文件修改：默认 `REQUIRE_APPROVAL`
- 密钥类、凭证类、私钥类文件读取或修改：可以直接 `BLOCK`
- 审计日志中永远不保存敏感文件内容，只保存路径、原因、脱敏摘要

建议强阻止类型：

```text
*.pem
*.key
id_rsa
id_ed25519
*.p12
*.jks
credentials
secrets
```

建议审批类型：

```text
.env
.env.*
```

实际策略可以在实现中配置，但必须明确记录命中的规则。

## 6. 命令安全边界

命令白名单不是字符串白名单，而是结构化规则。

MVP 中，命令白名单规则应至少包含：

```text
executable
argsPattern
cwdScope
allowPipe
allowRedirect
allowBackground
envPolicy
```

说明：

- `executable`：允许执行的主程序，例如 `mvn`、`npm`、`git`
- `argsPattern`：允许的参数模式，例如只允许 `test`
- `cwdScope`：允许的工作目录范围，默认必须在 trusted workspace 内
- `allowPipe`：是否允许管道
- `allowRedirect`：是否允许重定向
- `allowBackground`：是否允许后台执行
- `envPolicy`：环境变量继承和覆盖策略

MVP 默认规则：

- 白名单命令默认不允许管道
- 白名单命令默认不允许重定向
- 白名单命令默认不允许后台执行
- 白名单命令默认不允许内联脚本拼接
- 白名单命令默认只能在 trusted workspace 内执行

这些规则用于防止以下绕过：

```text
npm test && rm -rf ...
npm test | powershell ...
npm test > sensitive.txt
powershell -Command "远程脚本"
```

## 7. 审计强度边界

MVP 的“可审计”定位为：

```text
可追踪
可回放
可解释
```

具体含义：

- 可追踪：能看到 Agent 做过哪些动作
- 可回放：能按时间顺序重建主要执行过程
- 可解释：能看到关键动作的原因、权限判断和结果

MVP 不承诺：

```text
日志防篡改
合规审计级别
法律证据级审计
多方签名
远程审计归档
组织级审计策略
```

后续如果要升级为合规审计，需要增加：

- 事件签名
- 哈希链
- 只追加存储
- 远程归档
- 访问控制
- 审计日志保留策略
- 管理员和操作者职责分离

## 8. 文件变更证据

`FileChange` 不应只记录 diff，还应记录变更前后的证据。

MVP 建议字段：

```text
beforeHash
afterHash
baseRevision
observedAt
patchApplyStatus
lineAdded
lineDeleted
```

说明：

- `beforeHash`：变更前文件内容哈希
- `afterHash`：变更后文件内容哈希
- `baseRevision`：如果存在 Git，记录变更基准 revision
- `observedAt`：Agent 读取或观察目标文件的时间
- `patchApplyStatus`：patch 是否成功应用
- `lineAdded`：新增行数
- `lineDeleted`：删除行数

这些字段用于回答：

```text
Agent 修改的是它实际读过的那个版本吗？
用户是否在执行过程中手动修改过文件？
patch 是否应用到了预期内容？
本次变更影响范围有多大？
```

## 9. 权威规则摘要

MVP 中优先遵守以下规则：

```text
workspace 外路径：BLOCK
敏感文件读取：REQUIRE_APPROVAL，密钥类可 BLOCK
敏感文件修改：REQUIRE_APPROVAL，密钥类可 BLOCK
白名单命令：结构化规则匹配，不使用字符串前缀匹配
审计强度：可追踪 / 可回放 / 可解释，不是防篡改合规审计
AgentAction：原子动作意图
ToolCall / FileChange / CommandExecution：实际执行事实
ApprovalRequest：Runtime 暂停点
AuditEvent：追加写入的不可变事件记录
PermissionDecision：Runtime 权限裁决
```
