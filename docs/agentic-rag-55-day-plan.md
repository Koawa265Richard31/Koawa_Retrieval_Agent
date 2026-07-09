# KoawaAgent 55 天 Agentic RAG 学习与重开发规划书

> 用途：这是当前项目的长期上下文恢复锚点。后续如果聊天上下文丢失，先重新阅读本文件，再看当天开发日志，然后继续执行。

## 0. 新策略

原计划偏向“先读项目，再开发 agent loop”。实际学习效率不高，因为单看代码容易陷入细节。

新的策略改为：

```text
以 agent 工程化目标牵引阅读。
每天只读当天要改的链路。
每 1-2 天必须有一个小产出。
不再为了读而读。
```

核心原则：

- 读代码是为了完成一个工程化改动。
- 每次改动都必须尽量小，不能大范围重构。
- 原有 RAG pipeline 不删除，先旁路新增 agent 能力。
- 先实现最小可运行骨架，再逐步替换为真实能力。
- 每天保留学习日志，记录“今天读了什么、改了什么、没懂什么”。

## 1. 当前项目基线

当前项目已经不是简单 RAG demo，已有能力包括：

- Java/Spring Boot 多模块后端：`bootstrap`、`framework`、`infra-ai`、`mcp-server`
- React/Vite 前端管理台：`frontend`
- RAG 主链路：问题改写、子问题拆分、意图识别、检索、重排、prompt 组装、流式回答
- 向量库支持：Milvus 与 pgvector
- 模型基础设施：chat、embedding、rerank、provider routing、fallback、模型健康
- MCP 能力：MCP client、工具注册、工具执行、本地 mcp-server 示例工具
- 知识入库 pipeline：fetch、parse、chunk、enhance、enrich、index
- Trace：RAG run/node 追踪
- Memory：会话历史与摘要
- Admin：知识库、文档、chunk、意图树、ingestion、trace、模型设置等页面

当前主要不足：

```text
已有系统更像“静态 RAG pipeline + 意图路由 + 一次性 MCP 调用”。
还不是完整 agentic RAG。
```

当前主链路更接近：

```text
query rewrite
  -> intent classify
  -> retrieve / call MCP once
  -> build prompt
  -> answer
```

目标链路是：

```text
plan
  -> choose action
  -> execute retrieval/tool
  -> observe
  -> decide continue / clarify / answer
  -> final answer
```

## 2. 55 天最终目标

55 天内，把当前项目改造成一个学习级、面试可讲、可演示的 Agentic RAG 系统。

最终要能讲清楚：

```text
KoawaAgent
= 原有企业级 RAG 平台
+ Agent loop
+ 工具调用策略
+ 可观测 step trace
+ 检索与工具观察结果
+ 评估与安全控制
```

最终保留两条模式：

```text
普通 RAG 模式：
  沿用 StreamChatPipeline

Agentic RAG 模式：
  新增 AgentLoopRunner，复用 RetrievalEngine / MCP / Memory / Trace / SSE
```

## 3. 学习与编码规则

每天任务分四类：

```text
READ：只读当天要改的代码
DRAW：画当前链路或目标链路
HAND-CODE：自己手敲核心代码
VERIFY：运行最小验证
```

助手职责：

- 解释现有代码
- 帮你拆小任务
- 审查你写的代码
- 在你明确要求时补代码
- 维护规划书和开发日志

你需要手敲的内容：

- agent 核心 domain 类
- action 类型
- parser 逻辑
- loop runner 第一版
- 简单测试
- 关键接口适配

不建议你手敲的内容：

- 大量样板 VO/DTO
- 重复 mapper
- 前端 UI 大面积调整
- 复杂 Spring 配置细节

## 4. 新的总路线

不再先完整读完 RAG 项目，而是按功能切片推进。

```text
第 1 阶段：建立可启动/可开发环境 + 读主链路
第 2 阶段：新增 agent domain 和 action schema
第 3 阶段：做一个不接 LLM 的本地 agent loop 骨架
第 4 阶段：接入 RetrievalEngine，完成 RETRIEVE_KB action
第 5 阶段：接入 final answer，完成最小 Agentic RAG MVP
第 6 阶段：接入 MCP tool action
第 7 阶段：接入 trace、停止策略、错误策略
第 8 阶段：做评估、整理文档、准备迁移方案
```

## 5. 目标架构

新增 agent 层，不替换原有 RAG pipeline。

```text
User Question
  |
  v
AgentChatController
  |
  v
AgentChatService
  |
  v
AgentLoopRunner
  |
  +-- AgentPlanner
  |     输出结构化 AgentAction
  |
  +-- AgentActionExecutor
  |     |
  |     +-- RETRIEVE_KB -> 复用 RetrievalEngine
  |     +-- CALL_MCP_TOOL -> 复用 McpToolRegistry / McpToolExecutor
  |     +-- ASK_CLARIFICATION -> 直接流式提示用户澄清
  |     +-- FINAL_ANSWER -> 生成最终回答
  |
  +-- AgentObservation
  |
  +-- AgentStep Trace
  |
  v
SSE Final Answer
```

## 6. 建议新增包结构

```text
bootstrap/src/main/java/com/koawa/agent/agent/
  controller/
    AgentChatController.java
  service/
    AgentChatService.java
    AgentLoopRunner.java
    AgentPlanner.java
    AgentActionExecutor.java
  domain/
    AgentState.java
    AgentStep.java
    AgentAction.java
    AgentActionType.java
    AgentObservation.java
    AgentLoopResult.java
    AgentStopReason.java
  parser/
    AgentActionParser.java
  prompt/
    AgentPromptBuilder.java
  trace/
    AgentTraceSupport.java
```

Prompt 文件：

```text
bootstrap/src/main/resources/prompt/agent-planner.st
bootstrap/src/main/resources/prompt/agent-final-answer.st
```

测试：

```text
bootstrap/src/test/java/com/koawa/agent/agent/
  AgentActionParserTest.java
  AgentLoopRunnerTest.java
  AgentActionExecutorTest.java
```

## 7. 55 天重规划

### 第 1 阶段：开发环境与主链路定位，Day 1-3

目标：不再泛读，只定位 agent 要复用的旧能力。

Day 1 已完成：

- READ：`RAGChatServiceImpl`
- READ：`StreamChatPipeline`
- READ：`RetrievalEngine.retrieve`
- READ：`formatKbContext`
- VERIFY：尝试启动项目，确认当前阻塞在 RocketMQ
- DOC：整理 2026-07-08 日志

Day 2：

- READ：`MultiChannelRetrievalEngine`
- READ：`SearchChannel`
- READ：`VectorGlobalSearchChannel`
- READ：`IntentDirectedSearchChannel`
- DRAW：画“当前 KB 检索链路”
- HAND-CODE：不写业务代码，只写一份 `docs/retrieval-chain-notes.md`
- VERIFY：`mvnw -q -DskipTests compile`

Day 3：

- READ：`McpToolRegistry`
- READ：`McpToolExecutor`
- READ：`McpClientToolExecutor`
- READ：`mcp-server` 示例工具
- DRAW：画“MCP 工具注册与调用链路”
- HAND-CODE：写 `docs/mcp-tool-chain-notes.md`

阶段产出：

- `docs/retrieval-chain-notes.md`
- `docs/mcp-tool-chain-notes.md`
- 你能说清楚：agent 的 `RETRIEVE_KB` 和 `CALL_MCP_TOOL` 应该复用哪些类

### 第 2 阶段：Agent Domain 骨架，Day 4-7

目标：开始工程化，不再只读。

Day 4：

- READ：回看 `RetrievalContext`、`SubQuestionIntent`、`ChatRequest`
- HAND-CODE：创建 `AgentActionType`
- HAND-CODE：创建 `AgentAction`
- HAND-CODE：创建 `AgentObservation`
- VERIFY：编译通过

Day 5：

- HAND-CODE：创建 `AgentState`
- HAND-CODE：创建 `AgentStep`
- HAND-CODE：创建 `AgentLoopResult`
- HAND-CODE：创建 `AgentStopReason`
- DRAW：画 agent state 生命周期

Day 6：

- HAND-CODE：创建 `AgentActionParser`
- HAND-CODE：写 JSON 解析测试
- VERIFY：测试覆盖合法 JSON、非法 JSON、未知 action

Day 7：

- REVIEW：回顾 domain 是否过度设计
- ASSISTED-CODE：必要时调整字段和命名
- DOC：写 `docs/agent-loop-design.md` 第一版

阶段产出：

- agent domain 包
- parser 测试
- `docs/agent-loop-design.md`

### 第 3 阶段：无 LLM 的本地 Agent Loop，Day 8-12

目标：先做 loop 结构，不接真实模型。

Day 8：

- HAND-CODE：定义 `AgentPlanner` 接口
- HAND-CODE：实现 `ScriptedAgentPlanner`
- 说明：先用固定脚本模拟 planner 输出

Day 9：

- HAND-CODE：实现 `AgentLoopRunner` 第一版
- 支持：
  - maxSteps
  - 保存 steps
  - 遇到 FINAL_ANSWER 停止
  - 超步数停止

Day 10：

- HAND-CODE：实现 `AgentActionExecutor` 空壳
- 支持两个假 action：
  - `RETRIEVE_KB` 返回 mock observation
  - `FINAL_ANSWER` 返回 mock final answer

Day 11：

- HAND-CODE：写 `AgentLoopRunnerTest`
- 验证：
  - retrieve -> final answer
  - 超过 maxSteps 停止
  - planner 输出非法 action 时停止

Day 12：

- READ：回看 `RAGChatServiceImpl` 如何创建 SSE callback
- DRAW：画 agent endpoint 如何接入
- 暂不接真实接口

阶段产出：

```text
一个完全不依赖 LLM、不依赖 Redis/RocketMQ/DB 的 agent loop 单元测试。
```

这是最重要的加速点：先证明 loop 结构对，再接项目能力。

### 第 4 阶段：接入 RetrievalEngine，Day 13-18

目标：让 `RETRIEVE_KB` action 调用真实项目检索能力。

Day 13：

- READ：`RetrievalEngine.retrieve`
- READ：`SubQuestionIntent`
- READ：`IntentResolver`
- DRAW：agent action query 如何转成当前检索入参

Day 14：

- HAND-CODE：实现 `RetrieveKbActionHandler`
- 输入：
  - query
  - topK
- 输出：
  - AgentObservation
  - kbContext 摘要
  - intentChunks 数量

Day 15：

- HAND-CODE：补测试
- ASSISTED-CODE：如果构造 `SubQuestionIntent` 太复杂，可先做 adapter 或 fake intent

Day 16：

- READ：`MultiChannelRetrievalEngine`
- 修正 `RetrieveKbActionHandler` 的调用边界

Day 17：

- VERIFY：在无完整外部依赖时跑单元测试
- DOC：记录 `RETRIEVE_KB` action 如何复用旧系统

Day 18：

- REVIEW：检查是否破坏原有 `StreamChatPipeline`

阶段产出：

- `RETRIEVE_KB` action handler
- 测试
- 文档说明

### 第 5 阶段：Agentic RAG MVP，Day 19-25

目标：做出最小 agent 问答模式。

Day 19：

- HAND-CODE：定义 `AgentChatService`
- HAND-CODE：定义 `AgentChatController`
- 先不接前端，只提供后端接口

Day 20：

- HAND-CODE：接入 `AgentLoopRunner`
- 支持普通 HTTP 或 SSE 最小返回

Day 21：

- HAND-CODE：实现 `FINAL_ANSWER` handler
- 初版可以复用 `LLMService.streamChat`

Day 22：

- READ：`RAGPromptService`
- READ：`PromptContext`
- HAND-CODE：做 agent final answer prompt

Day 23：

- VERIFY：用 mock planner 跑完整 retrieve -> answer

Day 24：

- ASSISTED-CODE：如果 Spring wiring 卡住，让助手协助接 bean

Day 25：

- DOC：写 `docs/agent-mvp-notes.md`

阶段产出：

```text
Agentic RAG MVP：
一个问题可以经过 agent loop，
产生至少一个 action 和一个 final answer。
```

### 第 6 阶段：接入 MCP 工具，Day 26-32

目标：让 agent 可以选择工具。

Day 26：

- READ：`McpToolRegistry`
- READ：`DefaultMcpToolRegistry`
- READ：`McpClientToolExecutor`

Day 27：

- HAND-CODE：实现 `CallMcpToolActionHandler`

Day 28：

- HAND-CODE：将 MCP tool result 转成 `AgentObservation`

Day 29：

- HAND-CODE：工具失败不让整个 loop 崩溃

Day 30：

- VERIFY：用本地 `mcp-server` 示例工具测试

Day 31：

- DRAW：KB + MCP 混合回答链路

Day 32：

- DOC：写 `docs/agent-mcp-notes.md`

阶段产出：

- `CALL_MCP_TOOL` action
- 工具 observation
- 错误降级

### 第 7 阶段：真实 LLM Planner，Day 33-39

目标：把脚本 planner 替换为 LLM 结构化 planner。

Day 33：

- HAND-CODE：`LlmAgentPlanner`
- 使用 `LLMService`
- 输出 JSON

Day 34：

- HAND-CODE：planner prompt
- 要求固定 action schema

Day 35：

- HAND-CODE：planner JSON 解析失败重试

Day 36：

- HAND-CODE：重复 action 检测

Day 37：

- HAND-CODE：最大 tool call 限制

Day 38：

- VERIFY：mock LLM / fake planner 测试

Day 39：

- DOC：写 planner 设计说明

阶段产出：

- 真实 planner 实现
- 结构化输出解析
- 停止策略

### 第 8 阶段：Trace、评估与演示，Day 40-47

目标：证明 agent loop 真的在运行，并能比较效果。

Day 40：

- READ：`RagTraceRecordService`
- READ：`RagStreamTraceSupport`
- DRAW：agent step trace 结构

Day 41：

- HAND-CODE：agent step trace 记录

Day 42：

- HAND-CODE：记录 action、observation、stopReason

Day 43：

- HAND-WRITE：整理 20 个评估问题

Day 44：

- HAND-CODE：简单 eval runner

Day 45：

- VERIFY：比较普通 RAG vs agentic RAG

Day 46：

- DOC：写 `docs/evaluation-plan.md`

Day 47：

- REVIEW：整理缺陷和下一步

阶段产出：

- trace 可见 agent steps
- 小型评估集
- 对比结论

### 第 9 阶段：收尾、迁移方案、面试表达，Day 48-55

目标：形成最终可讲项目。

Day 48-49：

- DOC：写最终架构说明
- DRAW：最终架构图

Day 50-51：

- DOC：写 Python / TypeScript 迁移方案

Day 52：

- VERIFY：最终编译和核心测试

Day 53：

- HAND-WRITE：整理 demo 脚本

Day 54：

- HAND-WRITE：整理项目亮点和不足

Day 55：

- REVIEW：最终复盘

阶段产出：

- `docs/final-agentic-rag-architecture.md`
- `docs/language-ecosystem-migration-guide.md`
- demo 脚本
- 面试讲解稿

## 8. 每日执行模板

每天只写这几个字段：

```text
日期：
今日目标：
READ：
HAND-CODE：
DRAW：
VERIFY：
今日结论：
明日下一步：
```

每晚必须更新：

```text
docs/dev-log-YYYY-MM-DD.md
```

## 9. 当前最新进度

截至 2026-07-09：

已完成：

- 项目重命名为 KoawaAgent
- 新仓库干净历史推送
- 初步阅读 `RAGChatServiceImpl`
- 初步阅读 `StreamChatPipeline`
- 初步阅读 `RetrievalEngine`
- 理解 `formatKbContext`
- 本地启动排查到 RocketMQ 阻塞

当前应该继续：

```text
Day 2：
以实现 RETRIEVE_KB action 为目标，
阅读 MultiChannelRetrievalEngine 和 SearchChannel 系列。
```

今天不要再泛读整个项目。

今天的学习目标：

```text
搞清楚 retrieveKnowledgeChannels 如何把全局向量检索和意图定向检索合并。
```

今天的工程目标：

```text
为后续 AgentActionExecutor.RETRIEVE_KB 设计最小入参和出参。
```

## 10. 近期不要做的事

短期内不要做：

- 不要重写 `RetrievalEngine`
- 不要迁移 Python/TypeScript
- 不要大改前端
- 不要一上来做多 agent
- 不要直接接真实 LLM planner
- 不要为了启动项目改一堆配置

短期内应该做：

- 小步创建 agent domain
- 小步创建 parser
- 小步创建 fake planner
- 小步创建 loop runner
- 用测试先跑通 agent loop

## 11. 未来语言生态迁移

迁移不是现在做，而是在 Java MVP 跑通之后做。

语言无关边界：

- `AgentState`
- `AgentAction`
- `AgentObservation`
- `AgentPlanner`
- `AgentLoopRunner`
- `ToolRegistry`
- `Retriever`
- `TraceStore`

Python 方向：

```text
FastAPI + LangGraph + Pydantic
先做 sidecar agent orchestrator，
调用 Java retrieval/MCP API。
```

TypeScript 方向：

```text
NestJS/Hono + LangGraph.js + Zod
先做 agent gateway，
复用 Java 后端能力。
```

原则：

```text
不要大爆炸重写。
先让 Java 版本 agent loop 成型，
再考虑 sidecar。
```

## 12. 恢复上下文说明

如果后续上下文丢失：

1. 先读本文件。
2. 再读最近一天的 `docs/dev-log-YYYY-MM-DD.md`。
3. 执行 `git status --short`。
4. 搜索 agent 包是否已经存在。
5. 从当前阶段的下一个小任务继续。

常用命令：

```powershell
git status --short
rg "AgentLoopRunner|AgentState|AgentAction" bootstrap/src/main/java
rg "retrieveKnowledgeChannels" bootstrap/src/main/java
rg "agent-planner|agent-final-answer" bootstrap/src/main/resources/prompt
```
