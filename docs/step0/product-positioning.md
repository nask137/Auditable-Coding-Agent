# 可审计编码智能体产品定位说明

## 1. 产品定位

本项目定位为一个面向开发者的可审计编码智能体。它不是单纯的聊天机器人，也不是只封装 LLM Tool Calling 的工具层，而是一个能够在受控工作区内理解任务、读取代码、制定计划、修改文件、请求审批、执行验证并记录完整过程的 Agent Runtime。

第一版产品形态为本地运行的 Java 后端服务，对外提供 API 能力。CLI、Web 应用、桌面应用、IDE 插件等都可以作为客户端调用该后端服务。

建议第一阶段优先配套一个轻量 CLI 客户端，用于验证核心 Runtime、工具调用、权限控制和审计日志。桌面应用可以作为后续产品形态，在核心能力稳定后再推进。

## 2. 核心目标

产品的核心目标是让编码智能体在真实项目中可用、可控、可追踪。

第一版重点解决以下问题：

- 用户可以向 Agent 提出编码任务
- Agent 可以在用户信任的工作区内读取、创建和修改文件
- Agent 可以形成结构化执行计划
- Agent 每一步操作都有记录
- Agent 对高风险行为必须请求用户审批
- 用户可以查看文件变更、命令申请、审批结果和任务执行过程
- Agent 执行结束后可以生成可审计的任务报告

核心设计原则：

> LLM 负责判断、规划和生成；Runtime 负责状态、权限、流程、审计和恢复。

## 3. 第一版产品形态

第一版采用如下形态：

```text
本地 Java 后端服务
+
轻量 CLI 客户端
+
本地 Workspace 权限配置
+
本地审计日志
```

后端服务负责：

- Agent Runtime
- 任务管理
- 状态管理
- 工具调用
- 文件操作
- 命令审批
- 审计日志
- 文件变更记录
- LLM 能力封装

CLI 客户端负责：

- 提交任务
- 查看任务状态
- 展示执行事件
- 展示文件 diff
- 处理用户审批
- 查看执行报告

后续可以扩展出：

- Web 控制台
- 桌面应用
- IDE 插件
- 团队协作后台
- 工作流可视化编排器

## 4. 目标用户

第一版主要面向个人开发者，尤其是希望在本地项目中使用编码智能体辅助开发的人。

典型用户特征：

- 熟悉命令行或开发工具
- 希望 Agent 帮助读代码、改代码、补测试、修 bug
- 关注文件变更是否可控
- 不希望 Agent 随意执行命令或破坏项目
- 希望能查看 Agent 的执行过程和修改原因

未来可以扩展为团队协作场景：

- 团队共享 Agent 工作流
- 团队级权限策略
- 项目级记忆
- 执行记录归档
- 审计报告导出
- 多用户审批

## 5. 非目标范围

第一版暂不追求以下能力：

- 完整桌面应用体验
- 复杂 Web 可视化工作流编辑器
- 多 Agent 并行协作
- 团队权限体系
- 云端托管执行
- 插件市场
- 完整 IDE 级代码语义分析
- 自动执行任意 shell 命令
- 自动访问任意本地目录

这些能力可以作为后续阶段演进方向，而不是第一版的核心目标。

## 6. Workspace 边界

Agent 只能访问用户显式授权并信任的工作区。工作区是 Agent 执行文件操作的安全边界。

建议定义 Workspace 概念：

```text
Workspace
- workspaceId
- rootPath
- trusted
- allowedOperations
- createdAt
- lastUsedAt
```

第一版规则：

- Agent 只能访问 trusted workspace 内的文件
- Agent 默认不能访问 workspace 外路径；第一版中 workspace 外路径一律阻止，不进入普通审批流程
- Agent 默认不能修改 `.git` 目录
- Agent 默认不能读取或修改敏感文件，除非用户额外审批；密钥类、凭证类文件可以直接阻止
- 所有文件读取、创建、修改、删除、移动都必须记录到审计日志

## 7. 文件权限策略

在用户信任的 workspace 内，第一版默认允许：

- 读取文件
- 创建文件
- 修改已有文件

第一版默认需要审批：

- 删除文件
- 移动或重命名文件
- 读取敏感文件
- 修改敏感文件
- 修改构建或依赖配置
- 大范围文件修改
- 单次 patch 行数过多
- 修改 workspace 权限配置

建议将文件权限拆分为：

```text
FILE_READ：读取文件
FILE_CREATE：创建新文件
FILE_MODIFY：修改已有文件
FILE_DELETE：删除文件
FILE_MOVE：移动或重命名文件
```

建议默认敏感文件包括：

```text
.env
.env.*
*.pem
*.key
id_rsa
credentials
secrets
```

建议默认高影响文件包括：

```text
pom.xml
build.gradle
settings.gradle
package.json
package-lock.json
pnpm-lock.yaml
yarn.lock
Dockerfile
docker-compose.yml
```

这些文件可以允许修改，但应提高风险等级，并在审计日志中明确标记。

## 8. 命令执行策略

第一版默认不自动执行命令。Agent 只能提出命令执行申请，由 Runtime 根据命令策略判断是否允许执行。

命令分为三类：

```text
Allowlist Command：用户明确允许的命令
Approval Required Command：未加入白名单，但可以申请执行
Blocked Command：默认阻止或必须由用户手动执行
```

命令白名单必须是结构化规则，而不是字符串前缀匹配。第一版白名单规则至少包含：

```text
executable
argsPattern
cwdScope
allowPipe
allowRedirect
allowBackground
envPolicy
```

第一版默认不允许管道、重定向、后台执行和内联脚本拼接。白名单命令默认只能在 trusted workspace 内执行。

建议可加入白名单的命令：

```text
mvn test
./mvnw test
gradle test
npm test
npm run test
pytest
go test ./...
cargo test
git status
git diff
```

建议默认需要审批的命令：

```text
mvn package
npm install
docker compose up
git commit
git checkout
git merge
```

建议默认阻止的命令：

```text
rm -rf
del /s
format
chmod -R
curl | sh
远程脚本直接执行
```

命令执行审计必须记录：

- 命令内容
- 工作目录
- 申请原因
- 风险等级
- 是否命中白名单
- 审批结果
- 执行开始时间
- 执行结束时间
- 退出码
- 输出摘要

## 9. 必须审批和默认阻止的行为

第一版必须审批的行为包括：

- 删除文件
- 移动或重命名文件
- 执行未加入白名单的命令
- 读取敏感文件
- 修改敏感文件
- 修改构建或依赖配置
- 大范围文件修改
- 安装依赖
- 网络访问
- Git 写操作
- 启动长期运行进程

第一版默认阻止的行为包括：

- 访问 workspace 外路径
- 修改 `.git` 目录
- 路径穿越
- 写入系统目录
- 读取或修改密钥类、凭证类、私钥类文件
- 执行明确危险命令

如果用户确实希望 Agent 访问新的目录，应通过手动扩大 workspace 边界实现，而不是审批某次越界访问。

审批请求应包含：

```text
ApprovalRequest
- action
- reason
- riskLevel
- affectedFiles
- command
- patchPreview
- approvalStatus
```

用户可以选择：

- 允许本次操作
- 拒绝本次操作
- 将命令加入白名单
- 修改后再允许
- 终止任务

## 10. 审计定位

可审计是本产品的核心差异化能力。

第一版的可审计定位为可追踪、可回放、可解释：

- 可追踪：能看到 Agent 做过哪些动作
- 可回放：能按时间顺序重建主要执行过程
- 可解释：能看到关键动作的原因、权限判断和结果

第一版不承诺日志防篡改、法律证据级审计或合规审计级别能力。后续如果需要合规审计，应增加事件签名、哈希链、远程归档和审计日志保留策略。

系统需要能够回答：

- 用户提出了什么任务？
- Agent 如何理解任务？
- Agent 读取了哪些文件？
- Agent 为什么修改这些文件？
- Agent 创建或修改了哪些文件？
- 每次修改的 diff 是什么？
- Agent 申请执行了哪些命令？
- 哪些命令被允许或拒绝？
- 哪些行为经过了用户审批？
- 验证是否通过？
- 任务最终结果是什么？

所有关键动作都应该转化为事件，并进入审计日志。

## 11. 推荐 API 能力边界

后端服务第一版可以提供以下能力：

```text
POST /workspaces
GET  /workspaces
GET  /workspaces/{workspaceId}

POST /tasks
GET  /tasks/{taskId}
POST /tasks/{taskId}/start
POST /tasks/{taskId}/cancel

GET  /tasks/{taskId}/events
GET  /tasks/{taskId}/logs
GET  /tasks/{taskId}/changes
GET  /tasks/{taskId}/report

GET  /approvals
GET  /approvals/{approvalId}
POST /approvals/{approvalId}/approve
POST /approvals/{approvalId}/deny

GET  /command-policies
POST /command-policies/allowlist
DELETE /command-policies/allowlist/{id}
```

这些 API 不一定第一天全部实现，但可以作为产品边界和后续设计参考。

## 12. 第一版成功标准

第一版成功标准不是功能数量，而是能否完成一个小型真实编码任务，并完整记录过程。

建议验收场景：

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

## 13. 当前建议结论

第一阶段建议定稿为：

```text
一个运行在本机的 Java 后端 Agent Service，
提供任务执行、工具调用、权限审批、审计日志 API；
配套一个简单 CLI 客户端；
默认只操作用户信任的 workspace，workspace 外路径一律阻止；
默认允许读、创建、修改文件；
删除文件、移动文件、敏感文件读取或修改、大范围修改、未白名单命令执行必须审批。
```

这个方向符合当前 Java 后端能力优势，也为未来 CLI、Web、桌面端、IDE 插件和团队协作保留了扩展空间。
