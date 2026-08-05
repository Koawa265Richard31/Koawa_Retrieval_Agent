# ragent 工程设计、边界约束与面试作战手册

> 文档定位：这是 `ragent` 的总入口文档，用于恢复项目上下文、复习工程设计、统一简历口径和准备高压技术面试。
>
> 快照日期：2026-07-26。代码基线：`main` 分支 `a219ae7`。
>
> 事实来源：当前代码、Docker 配置、AR0～AR4 阶段记录、Spring AI 迁移记录和真实评测报告。
>
> 核心原则：只把已经实现并验证的能力说成现状；规划、迁移中能力和已知缺口必须显式区分。

---

## 0. 如何使用这份文档

### 0.1 状态标签

全文使用以下标签：

- **[已实现]**：当前代码中存在，并已经接入对应业务链路；不自动等于效果已验证。
- **[已验证]**：记录了验证时间、环境、用例或报告。
- **[有适配]**：存在实现或协议适配，但没有接入当前部署主链。
- **[迁移中]**：已经建立接口或最小切片，但尚未接管当前部署主链。
- **[目标]**：规划中的完成标准，不能当作现状写进简历。
- **[已知缺口]**：当前真实存在的风险或未闭环能力。

本文把“当前部署/远程实例”和“生产级”严格分开：

- **当前部署/远程实例**：目前真实运行的单机环境；
- **生产级**：具备完整安全、可用性、迁移、备份、监控和恢复能力的成熟度判断。

因此“已经部署”不等于“已经达到生产级”。

### 0.2 三轮复习法

第一轮只读以下章节，约 15 分钟：

1. 项目定位；
2. 一句话架构；
3. 两条顶层执行链；
4. 已实现、迁移中和未完成矩阵；
5. 面试禁止夸大清单。

第二轮按调用链读：

1. 文档摄取；
2. RAG 流式问答；
3. 多通道检索；
4. Agentic Retrieval；
5. 模型路由、记忆、Trace、取消。

第三轮进行压力面试：

1. 先遮住答案；
2. 每题按“结论—代码依据—取舍—限制—下一步”回答；
3. 回答超过两分钟还没说结论，重新组织；
4. 任何具体数字必须同时说明数据集和限制。

---

## 1. 项目定位

### 1.1 一句话定义

`ragent` 是一个面向企业知识库场景的、可部署和可评测的 RAG 工程项目，覆盖文档摄取、异步切片与向量化、查询改写、意图路由、多范围向量召回、会话记忆、流式回答、链路追踪和受控的 Agentic Retrieval 实验。

它不是：

- 一个套壳聊天页面；
- 一个通用 Agent Runtime；
- 一个已经完成多租户 ACL 的商用知识库平台；
- 一个已经全面迁移到 Spring AI 的项目；
- 一个高可用生产集群。

### 1.2 30 秒自我介绍版本

> 我做的是一个可部署的企业知识库 RAG 系统。离线侧用消息队列驱动文档解析、切片和向量化，在线侧把查询改写、意图路由、多范围召回、会话记忆和流式回答串成可追踪链路。相比只调用模型，我重点处理了失败降级、跨实例取消和 Agentic 检索灰度。当前是 2 核 8 GB 单机演示环境，pgvector 主链已跑通，ACL 和高可用仍是上线缺口。

### 1.3 两分钟项目介绍版本

> 这个项目解决的不是“怎么调用大模型”，而是企业文档从进入系统到被可靠检索和回答的整条链路。
>
> 摄取侧，原始文件放在 RustFS，元数据和任务状态放在 PostgreSQL。文档开始切片时通过 RocketMQ 事务消息把数据库中的 `RUNNING` 状态和消息提交绑定，消费者再执行解析、切片、Embedding 和索引替换。当前部署选 pgvector，是因为目标机器只有 2 核 8 GB，这样能减少一个独立向量服务，并且当前 Chunk 和向量可以共享数据库事务。代码保留 Milvus 适配，但切到 Milvus 后不能继续宣称跨存储原子性，需要 outbox 和对账。
>
> 查询侧，入口先经过认证、SSE callback、分布式排队限流和 Trace，再进入 RAG 或兼容期 Agent Loop。RAG 内部按记忆加载、查询改写、意图解析、歧义引导、检索、Prompt 编排和流式输出执行。检索不是简单搜一个 collection，而是意图命中时定向搜目标知识库，意图置信度不足时对所有知识库做全局向量兜底，最后统一去重和可选 Rerank。
>
> Agentic Retrieval 不是第三条顶层模式，而是 RAG 内部增强策略。它先使用首轮结果建立 EvidenceLedger，让模型判断证据是否充分；不足时在预算、超时、取消和重复查询约束下最多补检索一次。它支持 OFF、SHADOW、ACTIVE 和失败回退。但小规模对比没有证明召回收益，反而平均增加约 10.6 秒，所以默认关闭。这是我基于数据控制上线风险的一个例子。
>
> 当前还没有完成端到端 ACL、严格 SSE 幂等、自动数据库迁移和高可用部署。这些我会主动说明，并能给出具体改造方案。

### 1.4 为什么不是“直接去 Wiki 搜”

Wiki 搜索只解决“人在一个站点内手动找一篇页面”。企业知识助手还需要解决：

- 多来源文档统一接入和版本更新；
- 跨文档比较、条件判断和多跳问题；
- 文档权限、引用、拒答和审计；
- 历史追问与摘要；
- 可重复评测、效果回归和故障定位；
- 文档更新后旧索引替换；
- 与受控业务工具或 MCP 结果组合。

因此项目价值不在“把 Wiki 搬进向量库”，而在文档治理、检索编排、权限、评测和运行治理。

---

## 2. 当前状态：完成、迁移与缺口

### 2.1 状态矩阵

| 能力 | 当前状态 | 准确口径 |
| --- | --- | --- |
| 登录、注册、会话 | [已实现] | Sa-Token 会话，密码使用 BCrypt，兼容历史明文并在登录后升级 |
| 知识库、文档、Chunk 管理 | [已实现] | 有前后端 CRUD、文档预览、源文件和切片日志 |
| 可配置摄取 Pipeline | [已实现] | 当前是单 `nextNodeId` 的可配置链，不是通用 DAG |
| 异步文档切片 | [已实现] | RocketMQ 事务消息 + 消费者 |
| pgvector | [已验证] | 当前 Docker/远程实例使用，维度 1536 |
| Milvus | [有适配] | 有代码适配，不代表当前部署同时使用或已验证同等一致性 |
| 多通道检索 | [已实现] | 意图定向向量检索 + 全局向量兜底 |
| BM25 / Elasticsearch | [目标] | 枚举存在，实际通道未实现 |
| Rerank | [已实现] | 有处理链和百炼/noop 客户端；当前 Docker 部署配置关闭 |
| 会话窗口与摘要 | [已实现] | 最近轮次 + 异步 LLM 摘要 + Redis 分布式锁 |
| SSE、取消、Trace | [已实现] | 有完整事件协议和跨实例取消基础，但断链与幂等仍有缺口 |
| Agentic Retrieval AR0～AR4 | [已实现] | 有界增强检索、默认 OFF，业务收益尚未证明 |
| 引用编号 | [已实现] | 当次 Prompt 可把真实 Evidence 映射到 `[E1]`；最终输出未做 ID/逐句校验 |
| 企业资源级 ACL | [已知缺口] | 只有全文扩展有局部 owner/admin 校验，首轮检索和管理 API 未闭环 |
| Spring AI | [迁移中] | BOM、Prompt Mapper、同步 Invoker 已完成；未接入当前运行时 Bean |
| 通用 Agent Runtime | [迁移期兼容] | 当前仓库仍有实现，但长期归属独立 `KoawaAgent` |
| 单机部署 | [已验证] | 2 核 8 GB Compose 形态 |
| 高可用生产集群 | [目标] | 当前没有 HA、TLS、自动迁移、备份恢复和完整告警 |

### 2.2 当前可展示数据

2026-07-25 迁移后，生产 PostgreSQL 中有两套演示/评测知识库：

- `agentic-retrieval-eval-v1`：9 个文档、35 个 Chunk、35 个向量；
- `gakumas-agentic-demo-v1`：7 个文档、16 个 Chunk、16 个向量。

这些数据用于功能和评测演示，不代表真实企业规模。

### 2.3 仓库命名说明

根 POM 的 artifactId 和部分包名仍使用 `koawa-agent`，这是项目历史演进遗留。当前简历边界已经拆成：

- `ragent`：企业知识库 RAG 工程；
- `KoawaAgent`：通用可控 Agent Runtime。

面试时应主动把历史代码和目标边界讲清楚，不要为了命名一致而声称两个仓库仍是一个产品。

---

## 3. 总体架构

### 3.1 两个业务平面

```text
控制/摄取平面
  管理员 -> 知识库/文档/摄取配置
       -> RustFS 原始文件
       -> RocketMQ 异步任务
       -> Parse / Chunk / Embed / Index
       -> PostgreSQL + pgvector

在线问答平面
  用户 -> Nginx / React
       -> Auth / SSE / Rate Limit / Trace
       -> RAG Pipeline 或兼容 Agent Loop
       -> Memory / Rewrite / Intent / Retrieval / Prompt
       -> LLM Stream
       -> SSE + Conversation Persistence
```

分开两个平面的意义：

- 摄取是高耗时、可重试、以数据一致性为核心；
- 问答是低延迟、可取消、以用户体验和降级为核心；
- 二者不能共用同一种超时、事务和重试策略。

### 3.2 分层结构

```text
frontend
  React / Vite / Zustand / Axios / SSE
        |
bootstrap
  Controller
        |
  Application Service
        |
  RAG Orchestrator / Ingestion Engine / Domain Service
        |
  Repository / VectorStore / LLMService / MCP ports
        |
framework                         infra-ai
  通用契约、上下文、Trace、Web      模型/Embedding/Rerank 路由与供应商协议
        |                              |
PostgreSQL / Redis / RocketMQ / RustFS / SiliconFlow / MCP
```

### 3.3 Maven 模块职责

| 模块 | 职责 | 不应该承担 |
| --- | --- | --- |
| `framework` | 通用消息契约、用户上下文、异常、Web、Trace、MQ 辅助 | 具体 RAG 或供应商业务 |
| `infra-ai` | Chat、Embedding、Rerank 端口实现，模型选择、熔断与协议 | 知识库路由、证据充分性判断 |
| `bootstrap` | Spring Boot 装配及 RAG、知识库、摄取、用户业务 | 直接复制各供应商 HTTP 协议 |
| `mcp-server` | 独立 MCP 演示服务 | 企业知识库主链必需依赖 |
| `frontend` | 用户聊天和管理台 | 安全授权的最终边界 |
| `resources` | 数据库 schema、Prompt、评测和演示数据 | 运行时密钥 |
| `deploy` | Docker、Nginx、环境覆盖和运维脚本 | 业务规则 |

---

## 4. 顶层路由真相：只有两条执行链

### 4.1 正确模型

```text
AUTO 路由器
  ├─ RAG Pipeline
  │    ├─ Single-pass Retrieval
  │    └─ Agentic Retrieval
  └─ Agent Loop
```

顶层执行范式只有：

1. RAG Pipeline；
2. Agent Loop。

`AUTO` 是路由策略，不是业务链。`Agentic Retrieval` 是 RAG 内部检索策略，不是第三条顶层链。

### 4.2 当前代码中的概念债务

当前 `ChatExecutionMode` 和管理员下拉框把以下值放在同一维度：

```text
AUTO / RAG / AGENT / AGENTIC
```

其中 `AGENTIC` 实际行为是：

```text
进入 RAG Pipeline
  -> 完成首轮检索
  -> 在 AgenticRetrievalGateway 强制 Active
```

因此它只是管理员测试覆盖项。正确重构应拆为：

```text
ExecutionMode: AUTO / RAG / AGENT
RetrievalStrategy: AUTO / SINGLE_PASS / AGENTIC
```

生产用户不需要看到第二个维度；管理员调试时才可覆盖。

### 4.3 RAG 与 Agent 的边界

| 维度 | RAG Pipeline | Agent Loop |
| --- | --- | --- |
| 决策结构 | 预定义阶段 | 模型按状态选择下一 Action |
| 适用场景 | 稳定知识问答 | 工具选择和步骤不可预先确定 |
| 状态 | `StreamChatContext` | `AgentState` / `AgentStep` |
| 停止 | Pipeline 短路或流完成 | Final、Clarify、MaxSteps、Timeout、Cancel、Error |
| 风险 | 召回不足、Prompt 噪声 | 循环、越权、重复工具调用、成本不可控 |
| 当前项目定位 | 核心主链 | 迁移期兼容，长期归属 KoawaAgent |

---

## 5. 在线问答主链

### 5.1 入口链路

```text
GET /rag/v3/chat
  -> RAGChatController
  -> ChatExecutionMode 解析与管理员覆盖校验
  -> 创建 SseEmitter
  -> RAGChatServiceImpl.streamChat
       -> 生成/复用 conversationId
       -> 生成 taskId
       -> 创建 StreamCallback
       -> ChatQueueLimiter
       -> StreamChatTraceRunner
       -> executeRoutedChat
```

`RAGChatServiceImpl` 是 Application Service。它负责：

- ID 和一次请求生命周期；
- SSE callback；
- 限流和 Trace 包装；
- RAG/Agent 顶层路由；
- Agent 失败后的 RAG 回退。

它不应该负责：

- Query Rewrite 算法；
- 具体向量检索；
- Evidence 评估；
- 供应商 HTTP/SSE 协议。

### 5.2 RAG Pipeline

```text
StreamChatPipeline.execute
  1. loadMemory
  2. rewriteQuery
  3. resolveIntents
  4. handleGuidance
  5. handleSystemOnly
  6. RetrievalEngine.retrieve
  7. AgenticRetrievalGateway.route
  8. handleEmptyRetrieval
  9. RAGPromptService
 10. LLMService.streamChat
```

关键短路：

- 问题歧义高：返回澄清提示，不继续检索；
- 全部为系统意图：跳过知识库，直接生成；
- 普通 Single Pass 检索上下文为空：返回固定无结果提示；
- 用户取消：不能在 Agent 取消后重新启动 RAG。

当前 Active Agentic 有一个反例：Evidence 为空时，
`EvidenceContextPresenter` 仍可能写入非空“引用规则”文本，使
`handleEmptyRetrieval` 判断为非空并继续最终 LLM。这个路径依赖模型自行拒答，
应改为显式 `NO_EVIDENCE` 决策，不能把“空结果必然固定拒答”说成全链保证。

### 5.3 SSE 事件契约

正常事件序列：

```text
meta(conversationId, taskId)
  -> message(type=think|response, delta)
  -> finish(messageId, title)
  -> done
```

其他终态：

```text
cancel -> done
reject -> finish -> done（目标契约）
error -> connection error
```

当前 reject 实现还没有满足目标契约：入口创建 `StreamChatEventHandler` 时已经发送
`meta(originalConversationId, originalTaskId)` 并注册 task；排队超时或线程池拒绝后，
`ChatQueueLimiter.handleReject` 又生成新 taskId，再发送第二个 `meta`、reject、
finish、done，而且没有注销原 task。实际可能是：

```text
meta(original) -> meta(newTaskId) -> reject -> finish -> done
```

并留下原注册项直到缓存过期。修复时应复用入口已有 ID/handler，或延迟 handler
初始化；reject 必须对同一个 task 发送终态并注销。

`StreamChatEventHandler` 同时承担：

- 聚合 answer/thinking；
- 向前端分片发送；
- 完成或取消时保存 assistant 消息；
- 发送 finish/done；
- 注销 task。

### 5.4 ID 生命周期

| ID | 含义 | 生命周期 |
| --- | --- | --- |
| `userId` | 登录用户 | 跨会话 |
| `conversationId` | 一次多轮对话 | 多个用户 turn |
| `taskId` | 一次 Chat 请求对应的执行任务；Agent task 内可含多个 step/action | 单次请求 |
| `traceId` | 一次问答 Trace Run | 单次请求 |
| `messageId` | 一条持久化消息 | 长期 |
| `pipelineId` | 摄取流水线定义 | 多个摄取任务复用 |
| ingestion `taskId` | 一次摄取执行 | 单次文档处理 |

不能把 `conversationId` 当成一次请求 ID，也不能用 `taskId` 读取整段会话历史。

---

## 6. 检索链路

### 6.1 Query Rewrite 与意图解析

```text
原始问题 + 历史
  -> MultiQuestionRewriteService
  -> RewriteResult(rewrittenQuestion, subQuestions)
  -> IntentResolver
  -> SubQuestionIntent(subQuestion, NodeScore[])
```

`DefaultIntentClassifier` 当前是 LLM 分类器：

1. 把可用叶子意图节点放入 Prompt；
2. 让模型返回节点 ID 和 score；
3. Parser 根据 ID 找回受信任的 `IntentNode`。

它与 Agent Planner 的区别：

- Intent Classifier 在固定 Pipeline 中做分类；
- Agent Planner 根据完整 AgentState 决定下一 Action。

### 6.2 当前实际检索通道

#### 意图定向通道

`IntentDirectedSearchChannel`：

- 只使用 KB 类型意图；
- 根据意图节点绑定的 collection 定向检索；
- 多个意图并行执行；
- 优先级高。

#### 全局向量兜底

`VectorGlobalSearchChannel`：

- 未识别意图时启用；
- 最大意图分数低于阈值时启用；
- 单一中置信度意图可补充启用；
- 枚举所有未删除知识库 collection 并行检索。

注意：两者都是向量检索，只是检索范围和启用条件不同。当前不能声称已经实现 BM25、Elasticsearch 或稀疏—稠密混合召回。

### 6.3 多通道编排

```text
MultiChannelRetrievalEngine
  -> 筛选 enabled SearchChannel
  -> 按独立线程池并行
  -> 单通道异常降级为空结果
  -> 合并结果
  -> DeduplicationPostProcessor
  -> 可选 RerankPostProcessor（启用时在此 TopK）
```

去重规则：

- 优先使用 Chunk ID；
- 没有 ID 时退化为文本 hash；
- 重复 Chunk 保留更高 score。

这里的限制：

- 文本 `hashCode` 不是可靠业务唯一键；
- 当前没有跨通道统一分数归一化或 RRF；
- Rerank 在当前 Docker 部署配置中关闭；
- Rerank 关闭时 Engine 返回去重后的全集；只有 `ContextFormatter` 对进入 Prompt
  的文本做 TopK，`intentChunks` 仍可能保留更多结果；
- 全局通道当前会搜索所有知识库，尚未下推用户 ACL。

还有一个重要的 provenance 缺口：当前 `RetrievalEngine` 无法稳定证明每个 Chunk
最初属于哪个 sub-question/intent，部分结果会被复制进多个 KB intent 桶。
这会放大 Evidence 数量，让多个任务看起来都已获得证据，也可能抬高小样本召回指标。
后续 `SearchResult` 必须显式保留 `channelId`、`subQuestionId/taskId`、`intentId`、
`kbId`、`docId` 和原始 score，Evidence 只能按真实来源建账。

### 6.4 单请求调用放大

复杂请求可能同时触发：

```text
1 次 Rewrite LLM
  + 每个子问题 1 次 Intent LLM
  + 可选歧义判断
  + 每个子问题 × 候选知识库的检索 fan-out
  + Agentic Evaluator
  + 可选 Planner + 补检索 + 第二次 Evaluator
  + 1 次最终生成
```

当前 Rewrite Parser 没有完整限制 `sub_questions` 数量，部分并行
`CompletableFuture.join()` 也缺少阶段 deadline；全局通道还会按所有知识库 fan-out。
因此不仅要限制 Agentic 的轮次，还要给基线 Pipeline 设置：

- 最大子问题数；
- 最大候选知识库数；
- 整个请求的模型调用、Token、检索次数和并行任务预算；
- Rewrite、Intent、Retrieval、Generation 分阶段 deadline；
- 超预算后的明确降级与 Trace 标记。

### 6.5 MCP 在 RAG 中的实际行为

当前 RAG MCP 是“意图路由驱动”，不是模型原生 Tool Calling：

```text
命中 MCP 意图
  -> IntentNode.mcpToolId
  -> McpToolRegistry 找 executor
  -> McpParameterExtractor 根据问题和 Tool Schema 抽参数
  -> MCP executor
  -> ContextFormatter
```

当前 bundled MCP 的天气、销售、工单主要是演示工具，不能描述为已经接入真实企业系统。

### 6.6 Prompt 编排

`RAGPromptService` 根据上下文选择：

- KB_ONLY；
- MCP_ONLY；
- MIXED；
- EMPTY。

SYSTEM-only 请求由 `StreamChatPipeline.handleSystemOnly` 在检索和
`RAGPromptService` 之前短路，不属于 PromptScene。

Prompt 层负责把已确定的业务上下文转换成模型消息，不负责：

- 再查数据库；
- 决定知识库 ACL；
- 放宽检索预算；
- 信任模型生成的任意引用或工具参数。

---

## 7. 文档摄取链路

### 7.1 知识库文档主流程

```text
上传文件/远程来源
  -> RustFS 保存原始对象
  -> t_knowledge_document(PENDING)
  -> startChunk
  -> RocketMQ 事务消息
       本地事务：document.status = RUNNING
  -> KnowledgeDocumentChunkConsumer
  -> Extract
  -> Chunk 或自定义 Pipeline
  -> Embed
  -> 替换旧 Chunk 和旧向量
  -> document.status = SUCCESS / FAILED
  -> chunk log 记录阶段耗时
```

### 7.2 两条执行入口

当前不能笼统地说“所有摄取都由 MQ 异步执行”：

1. **知识库文档主链**：上传/远程文档进入知识库后，通过 RocketMQ
   异步触发切片和索引；
2. **通用 Ingestion Task**：`IngestionTaskService` 同步驱动可配置
   `IngestionEngine`，用于直接执行 Pipeline。

第二条路径可能把文件读取、解析、LLM/Embedding 和索引等慢操作放进较长的
Service/事务生命周期。正式演进应把“任务状态事务”和“外部慢调用”拆开，
用异步 worker、状态机和幂等 checkpoint 执行。

### 7.3 两种处理模式

#### CHUNK 模式

固定流程：

```text
Tika Extract
  -> ChunkingStrategy
  -> ChunkEmbeddingService
  -> Persist
```

#### PIPELINE 模式

通过 `IngestionEngine` 执行配置链：

```text
Fetcher -> Parser -> Enhancer -> Chunker -> Enricher -> Indexer
```

Pipeline 使用 `skipIndexerWrite=true` 时，Indexer 只完成前置处理，最终持久化仍回到 `KnowledgeDocumentServiceImpl`，避免同一文档出现两个写入入口。

### 7.4 为什么说它不是通用 DAG

当前节点通过单个 `nextNodeId` 串联，支持：

- 基础环检测；
- 节点存在性校验；
- 条件跳过；
- 节点日志；
- 失败停止。

它没有：

- 多分支汇合；
- 拓扑并行；
- 动态补偿边；
- 通用工作流版本调度。

准确说法是“可配置摄取链”，不是“自研通用 DAG 引擎”。

### 7.5 为什么使用 RocketMQ 事务消息

需要避免：

```text
数据库已经改为 RUNNING，但消息没发出去
```

或：

```text
消息已经可消费，但数据库事务回滚
```

当前设计：

1. 发送事务消息；
2. 本地事务把文档状态更新为 `RUNNING`；
3. Broker 不确定时回查数据库状态；
4. 只有状态成立才提交消息。

但它不是 exactly-once：

- RocketMQ 消费仍是至少一次；
- 当前消费者没有消费幂等表或原子 claim；
- 重投可能重复执行；
- 消费失败被捕获并标记 `FAILED` 后没有继续抛出，Broker 会认为已消费；
- 单 Broker 异步刷盘仍有丢失窗口。

正确改进是：

- `eventId/docId + processingVersion` 幂等键；
- 消费前用条件更新 claim；
- 失败进入可重试状态或 DLQ；
- 定时扫描长时间 RUNNING/FAILED；
- 保留人工重放和对账。

### 7.6 Chunk 与向量是否原子

当前部署环境使用 pgvector：

- Chunk 表和向量表都在同一个 PostgreSQL；
- `TransactionOperations` 可以覆盖删除旧数据、写新 Chunk、写新向量和更新文档状态；
- 当前部署下可以获得数据库事务原子性。

切换 Milvus 后：

- Spring 数据库事务无法回滚外部 Milvus 写入；
- 不能继续称为 ACID 原子；
- 需要 outbox、索引版本、幂等 upsert、补偿删除和 reconciliation。

### 7.7 增量更新语义

远程来源可利用 ETag、Last-Modified 或内容 hash 判断变化。更新成功时采用“旧索引替换”而不是无界追加。

面试必须补充：

- 对象存储不在数据库事务里；
- 上传对象后数据库失败可能留下孤儿对象；
- 删除对象是 best effort；
- 应通过对象状态、延迟清理任务和定期对账闭环。

### 7.8 Wiki 转知识库

Wiki 与普通 RAG 的主流程相同，但采集层需要额外处理：

```text
MediaWiki/API Fetch
  -> 去导航、模板和无关标记
  -> 保留标题、章节、URL、revisionId、revisionTime、license
  -> 按章节结构切片
  -> Embedding / Index
  -> revision 变化时增量替换
```

不能只抓取页面可见文本后丢失版本和归因信息。当前学园偶像大师演示数据保留了 URL、revision 和 CC BY-SA 归因。

---

## 8. Agentic Retrieval

### 8.1 为什么它属于 RAG

它操作的是：

- RetrievalPlan；
- RetrievalTask；
- EvidenceItem；
- EvidenceLedger；
- RetrievalBudget；
- RetrievalStopReason。

它不负责：

- 最终用户答案；
- 任意工具调用；
- 通用 Agent State；
- 修改知识库；
- 多 Agent 协作。

因此它是“RAG 内部有界检索编排器”，不是通用 Agent。

### 8.2 实际闭环

```text
首轮 RetrievalContext
  -> RetrievalPlanFactory
  -> RetrievalContextEvidenceAdapter
  -> EvidenceLedger
  -> DeterministicEvidenceChecks
       ├─ 空 Ledger / 结构性充分：直接返回，不调用 LLM
       └─ 需要语义判断：LLM EvidenceEvaluator
       ├─ 充分：SUFFICIENT
       ├─ 请求 FULL_DOCUMENT_CONTEXT：受控扩展后再评估
       └─ 不足：Follow-up Planner
              -> 受约束 Parser / 执行模型转换
              -> Deadline / Cancel / Budget / Duplicate Guard
              -> 最多一次补检索
              -> 新 Evidence 去重合并
              -> 再次 Evaluation
              -> SUFFICIENT / BUDGET_EXHAUSTED / 其他 StopReason
```

### 8.3 为什么先做首轮检索

首轮结果同时承担：

1. Agentic 的初始 Evidence；
2. Agentic 失败时的稳定回退；
3. Shadow 与 Single Pass 的同请求对照基线。

代价是 Active 永远先支付首轮成本，因此只能把它用于复杂查询，不能默认让所有问题进入。

### 8.4 模式和灰度

| 模式 | 用户回答使用 | Agentic 是否执行 | 用途 |
| --- | --- | --- | --- |
| OFF | 首轮结果 | 否 | 默认与立即回滚 |
| SHADOW | 首轮结果 | 异步执行 | 观测收益、延迟和失败 |
| ACTIVE | 增强结果或首轮回退 | 同步执行 | 小流量验证 |

SHADOW 只保证“不改变本次回答使用的上下文”，不代表没有副作用：
它仍消耗模型配额、费用、线程、CPU 和网络，并可能与用户请求竞争供应商限额。
正式灰度需要独立 bulkhead、配额、成本指标和自动熔断。

SHADOW 与 ACTIVE 目前也不是完全同构：Shadow Runner 调用四参数
orchestrator，principal 为 null，全文扩展 ACL 会 fail-closed；ACTIVE 调五参数
并传入 userId/username/role。因此需要 `FULL_DOCUMENT_CONTEXT` 时，Shadow
无法执行 ACTIVE 可能执行的授权全文扩展，会低估或偏离 ACTIVE 效果。
修复方式是让 Shadow 携带同一份 immutable principal snapshot，同时保持结果隔离。

稳定分桶：

```text
conversationId 优先
  -> 缺失时 userId
  -> hash mod 100
```

这样同一会话不会在多轮中随机切换策略。

### 8.5 当前预算

- 总预算：120 秒；
- Evaluator：45 秒；
- Planner：30 秒；
- 最大轮次配置：2；
- 单次 Follow-up Planner 最大输出查询：6；
- 最大 Evidence Chunk：40；
- 每次 Evaluator payload 最大 Evidence 条数：20；
- 每次 Evaluator payload 中单条 Evidence 最大字符数：1200；
- `FullDocumentExpander` 每次 `expand(hit)` 最大字符数：12000。

当前编排代码实际上固定为“首轮 + 最多一次补检索”。即使配置字段叫 `maxIterations`，不能描述为任意多轮循环。
`maxSubQueries=6` 不限制 Rewrite 初始 `sub_questions`；
Evaluator 的 20 条/1200 字符也不限制整个 EvidenceLedger 或最终回答 Prompt；
全文 12000 字符会在每次命中文档扩展时重新计算，不是请求级全局字符预算。
这些局部限制不能替代第 6.4 节的请求级总预算。

历史配置与当前配置不要混答：

| 阶段 | 总预算 | Evaluator | Planner | 含义 |
| --- | --- | --- | --- | --- |
| AR1/AR2 初始验收 | 8s | 8s | 8s | 最初保护性预算，真实供应商频繁超时 |
| AR2 临时真实验收 | 30s | 10s | 10s | 用于确认超时与回退路径 |
| 当前代码/部署默认 | 120s | 45s | 30s | 为链路可验证放宽，不代表目标 SLA |

面试时先报当前值，再解释历史值只属于阶段实验。

### 8.6 为什么模型输出必须经过 Parser

Planner 和 Evaluator 输出属于不可信输入。当前 Parser 会拒绝：

- 非法 JSON；
- 未知 taskId；
- 数组中重复的 taskId；
- 缺失任务；
- 越界置信度；
- 超过最大查询数。

`RetrievalPlanParser` 的 Gson DTO 只接收 taskId/query；模型额外输出的 `kbId`、
`sql` 等未知控制字段当前会被忽略，而不是显式报错。安全性来自转换到执行模型时
丢弃这些字段，并复用原 task 与原 `NodeScore`，所以 Follow-up 不能扩张现有路由范围。
但首轮路由本身尚无完整 ACL，不能把它称为“不扩张授权范围”。

如果要称为严格 schema，还应显式拒绝 unknown fields 和重复 JSON key，
并用恶意 Planner 输出补测试。

### 8.7 停止原因

| StopReason | 语义 | Gateway 处理 |
| --- | --- | --- |
| `SUFFICIENT` | 证据充分 | 使用 Evidence 上下文 |
| `BUDGET_EXHAUSTED` | 达到轮次或 Chunk 上限 | 可使用已有 Evidence |
| `NO_NEW_EVIDENCE` | 补检索没有新增证据 | 可使用已有 Evidence |
| `DUPLICATE_QUERY` | Planner 重复已有查询 | 停止继续检索 |
| `CANCELLED` | Agentic 阶段观察到用户取消 | 当前 Gateway 回退首轮 |
| `TIMEOUT` | 总或组件 deadline 到达 | 回退首轮 |
| `PLANNING_FAILED` | Follow-up 计划失败 | 回退首轮 |
| `RETRIEVAL_FAILED` | 补检索失败 | 回退首轮 |
| `EVALUATION_FAILED` | 证据评估失败 | 回退首轮 |

需要警惕：当前 Pipeline 只有“是否为空”的统一判断，尚未完整区分“部分充分、应拒答、超时但有证据”等回答策略。

取消还有一处正确性缺口：Agentic Gateway 把 `CANCELLED` 当增强失败并返回
single-pass，Gateway 之后、最终 LLM 请求之前缺少一次显式取消检查。
随后 `bindHandle` 虽可取消已标记任务，但模型调用可能已经短暂启动。
因此不能承诺“Agentic 取消一定立即终止”；应在最终生成前再次检查取消，
并让取消终态禁止任何回退链重新启动上游调用。

### 8.8 引用与冲突做到什么程度

已实现：

- 当次 Presenter 按 Evidence 顺序生成 `[E1]`、`[E2]`；它们只在本次回答上下文内稳定，
  不跨请求稳定；
- Citation 保留 Chunk、文档、标题和来源 URI；
- 注入 Prompt 的 citation catalog 只来源于真实 Evidence，不为虚构编号创建占位来源；
- `CONFLICTED` 任务向回答 Prompt 注入冲突说明要求；
- 全文扩展只能从已命中 Evidence 出发。

尚未实现：

- SSE 中结构化发送 citation catalog；
- 前端完整引用卡片和来源跳转；
- 逐句 claim—citation 对齐；
- 判断引用是否真正支持结论；
- 对最终流式文本做引用 ID 和覆盖率校验。

模型仍可能在流式答案中输出不存在的 `[E99]`。所以准确口径是
“为本次回答构造源自 Evidence 的引用目录”，不是“保证模型输出的每个编号存在”
或“保证每句话事实正确”。

### 8.9 全文扩展安全边界

只有 Evaluator 输出精确标记 `FULL_DOCUMENT_CONTEXT` 才允许扩展。扩展前重新校验：

1. 命中 Chunk 存在且启用；
2. Chunk 的 docId/kbId 与 Evidence 一致；
3. 文档存在、启用且属于同一知识库；
4. 知识库存在；
5. `DocumentAccessPolicy` 授权；
6. 扩展 Chunk 仍属于同一文档和知识库；
7. 字符数和总 Chunk 预算未超限。

当前策略只覆盖管理员或资源所有者，组织共享知识库尚未实现。

### 8.10 真实评测结论

AR0：

- 25 题、9 份文档；
- Recall@5 = 100%；
- MRR@5 ≈ 97.73%；
- 无答案问题空召回率 = 0%；
- 平均延迟约 10.45 秒；
- P95 约 18.42 秒。

这些数字不能证明生产效果，因为数据集很小、文档级 Recall 已饱和，而且无答案场景表现差。

Gakumas 5 题对比：

- Single Pass 来源召回率 = 100%；
- Active 来源召回率 = 100%；
- Agentic 平均额外延迟约 10.6 秒；
- Active 回退率约 20%；
- 生产复杂度规则命中率为 0%；
- ACL 未真实覆盖。

结论：

> Agentic Retrieval 的工程闭环已经建立，但当前数据没有证明质量提升，所以默认关闭。完成可灰度、可回退和可评测，不等于已经产生业务收益。

---

## 9. 兼容期 Agent Loop

### 9.1 当前模型

```text
AgentPlanner
  -> AgentAction
  -> RoutingAgentActionExecutor
  -> AgentActionHandler
  -> AgentObservation
  -> AgentStep
  -> 下一轮 Planner
```

四种 Action：

- `RETRIEVE_KB`；
- `CALL_MCP_TOOL`；
- `ASK_CLARIFICATION`；
- `FINAL_ANSWER`。

### 9.2 Runner、Executor、Handler 的边界

`AgentLoopRunner`：

- 控制步骤、取消、总超时和停止；
- 调用 Planner 和 Executor；
- 保存 Step；
- 发布事件；
- 不解析具体工具参数。

`RoutingAgentActionExecutor`：

- 根据 ActionType 查找 Handler；
- 不决定下一步；
- 不吞掉 Handler 的未知异常。

Handler：

- 解释某一种 Action；
- 调用具体 KB/MCP/Final 能力；
- 返回 Observation；
- 可恢复的工具失败应返回 `success=false` 和错误内容，让 Planner 判断是否换工具或重试。

### 9.3 StopReason 与 FailureType

`AgentStopReason` 表示整个 turn 为什么停止：

- FINAL_ANSWER；
- ASK_CLARIFICATION；
- MAX_STEPS；
- TIMEOUT；
- CANCELLED；
- ERROR。

`AgentFailureType` 表示失败属于哪一类，用于恢复策略和诊断。

不要混淆：

- Observation 失败：一次 Action 没成功，Loop 仍可能继续；
- StopReason.ERROR：Runner 无法继续；
- errorMessage：给日志和诊断用，不等于流程必然失败。

### 9.4 恢复预算

当前只对：

- 空模型响应；
- 非法 Action 响应；

允许一次 Planner 恢复。恢复计数属于整个 turn，不是每个 step 各一次。

模型调用失败、Action 执行异常和未知异常直接停止，避免把不可恢复错误包装成无限重试。

### 9.5 工具安全

`PreparedToolCall` 是执行策略检查所需的受控载体，封装：

- toolId；
- 已解析参数；
- Tool executor/定义。

`AllowListAgentExecutionPolicy` 在执行前只允许显式配置的 Tool ID。它解决“是否允许执行”，不替代：

- 参数 schema 校验；
- 用户权限；
- 幂等；
- 高风险操作确认；
- 审计。

### 9.6 ragent 中的长期边界

这套通用 Agent Runtime 已经迁移为独立 `KoawaAgent` 项目的核心。`ragent` 中保留的是兼容链路，不应继续演进多 Agent、Supervisor、Skills 或通用工作流。

如果知识问答需要工具：

- 优先采用知识业务相关的 Spring AI Tool Calling 或 MCP；
- 工具必须只读或受控；
- RAG 业务编排仍留在 ragent。

---

## 10. 模型基础设施与 Spring AI 边界

### 10.1 当前部署态模型链

```text
业务代码
  -> LLMService
  -> RoutingLLMService
  -> ModelSelector
  -> ModelRoutingExecutor
  -> ModelHealthStore
  -> 自研 ChatClient
  -> AbstractOpenAIStyleChatClient
  -> OkHttp + 自研 SSE Parser
```

Embedding：

```text
业务代码
  -> EmbeddingService
  -> RoutingEmbeddingService
  -> 自研 EmbeddingClient
  -> Provider API
```

### 10.2 模型路由和熔断

候选选择考虑：

- default model；
- deep-thinking capability；
- priority；
- enabled；
- provider 配置；
- 当前熔断状态。

健康状态：

```text
CLOSED
  -> 连续失败达到阈值
OPEN
  -> open duration 到期
HALF_OPEN
  -> 单次探测成功回 CLOSED
  -> 失败重新 OPEN
```

同步调用：

- 普通可恢复异常尝试下一个候选；
- `DEADLINE_EXCEEDED` 不再 fallback，避免在总预算耗尽后继续花钱和拖延。

流式调用：

- 启动失败、首包超时、首包前 error/no-content 可切下一个模型；
- 已向用户发出内容后不能安全切模型，否则可能重复或语义断裂。

### 10.3 当前部署中的真实限制

代码支持多候选，不等于部署已经多供应商容灾。当前 Docker 配置通常只有一个 SiliconFlow Chat 和一个 Embedding 候选。

准确说法：

> 模型路由和熔断抽象已经实现；当前单机部署只有单一真实候选，尚未验证跨供应商容灾。

### 10.4 Spring AI 到哪一步

[迁移中] 当前已经：

- 引入 Spring AI 1.1.8 BOM；
- `infra-ai` 引入 `spring-ai-model`；
- 实现 `SpringAiPromptMapper`；
- 实现 `SpringAiSyncChatInvoker`；
- 覆盖消息映射、Options 和空响应分类测试；
- 故意不注册为 Spring Bean。

当前还没有：

- 接管 `LLMService`；
- Provider Bean 与配置切流；
- Spring AI Embedding；
- Spring AI 流式 Flux 到现有 callback 的适配；
- Spring AI MCP；
- 生产回归和默认切换。

因此不能说“项目基于 Spring AI”，只能说：

> 项目正在按适配器模式渐进迁移 Spring AI，目前完成依赖验证和同步 Chat seam，当前主链仍是自研模型适配层。

### 10.5 为什么不让 Spring AI 替换整个 RAG

Spring AI 适合负责：

- ChatModel / EmbeddingModel；
- Provider 协议；
- 流式模型调用；
- Tool/MCP 标准接入；
- 基础 Observation。

项目必须保留：

- 摄取状态机；
- Query Rewrite；
- 意图树和业务路由；
- 多通道召回；
- ACL；
- Evidence 与引用；
- 会话、反馈和业务 Trace；
- 评测和灰度。

原则：

> Spring AI 位于模型协议边界，自研能力位于业务编排边界。

---

## 11. 会话记忆、取消、限流与 Trace

### 11.1 会话记忆

```text
load
  -> 并行加载最新摘要
  -> 并行加载最近 N 轮
  -> 摘要作为 system message
  -> 返回历史快照

append assistant
  -> 异步检查摘要阈值
  -> Redis Lock 防止同会话并发摘要
  -> 合并旧摘要与待压缩消息
  -> 写入新摘要
```

默认策略：

- 保留最近 4 轮；
- 第 5 轮开始考虑摘要；
- 摘要上限 200 字符。

降级：

- 摘要加载失败：只用最近历史；
- 历史加载失败：空历史继续；
- 摘要生成失败：保留旧摘要。

取舍：问答可用性优先，但记忆降级必须通过 Trace/日志可观察，否则容易把“忘记上下文”误判成模型问题。

### 11.2 跨实例取消

`StreamTaskManager` 使用：

- 本地 task cache；
- Redis cancel bucket；
- Redis Pub/Sub；
- 上游 `StreamCancellationHandle`。

关键竞态处理：

- 先取消、后绑定 handle：`bindHandle` 发现已取消后立即 cancel；
- 多节点取消：Redis 标记 + Pub/Sub 通知；
- 本地重复取消：CAS 保证一次终态。

当前缺口：

- `/rag/v3/stop` 只接受 taskId，没有校验 task 所属用户；
- emitter 的 completion/timeout/error 当前只取消排队 Ticket；
- Ticket 进入 `GRANTED` 后再 cancel 不会转为取消态，也没有继续调用
  `StreamTaskManager.cancel(taskId)`，因此浏览器断链后，活跃模型请求仍可能继续、
  落库和计费；
- Redis Pub/Sub 不持久。cancel bucket 可以覆盖“先取消、后 register/bind”，
  但已注册任务若在订阅中断时漏掉消息，当前没有周期性 reconcile；
- task 所有权应持久化或写 Redis，并原子校验。

修复边界：

- emitter 三个终止回调统一触发 owner-aware task cancel；
- queue ticket、SSE emitter、upstream handle 使用一个终态 CAS，避免重复取消；
- 对取消命令使用持久化 Stream/状态轮询，或周期核对 cancel bucket；
- 端到端验证“排队中断”和“流式中断”都不再继续调用或落库。

### 11.3 分布式排队限流

设计包含：

- Redis ZSet 排队；
- Lua 原子 claim；
- Pub/Sub 和轮询唤醒；
- 可过期 semaphore；
- 线程池拒绝时走统一 reject。

“公平”表示尽量接近 FIFO，不是严格实时公平。

当前关键缺口：

- permit lease 约 30 秒；
- 模型首包可能等待 60 秒；
- SSE 可运行 180 秒；
- permit 未续租，可能过早释放；
- Runnable 在拿到流式 handle 后返回，permit 不覆盖完整 SSE 生命周期。

还有一处容易被忽略的上下文边界：业务线程池使用 TTL 传播
`UserContext`，但排队限流器自建的 scheduler 未包装。queued grant/timeout
由 scheduler 再提交到业务线程池时，可能捕获错误或陈旧的 ThreadLocal。
主 RAG 参数虽然已经显式快照 userId、username 和 role，Trace 与 reject
落库仍有读取 ambient `UserContext` 的路径，存在错归属风险。

正式方案应让排队请求显式携带 immutable principal snapshot，执行前 set、
`finally` clear，并补两个用户并发排队、超时和拒绝的隔离测试。

因此“聊天并发 2”的准确口径是：

> 当前主要约束排队、检索和流启动阶段，不保证全生命周期同时活跃流严格等于 2。

### 11.4 Trace

Trace 分两层：

1. `StreamChatTraceRunner`：Run 生命周期、完整链路 TTFT、成功/失败；
2. `@RagTraceNode`：Rewrite、Intent、Retrieval、LLM 等业务节点。

优点：

- Trace 数据表默认不保存完整问题、Prompt 和答案，只保存 question length、
  状态、耗时和截断错误；
- 正常完成/error 通过 trace-aware callback 驱动 Run 终态；
- 已包装的业务线程池会传播并清理 ThreadLocal；未包装 scheduler 不能依赖隐式上下文，
  应改为显式 principal snapshot。

缺口：

- 没有 Prometheus/Micrometer 的 `rag_agentic_*` 指标；
- 没有真实 Token Usage 和请求级模型调用计数；
- queue wait 不一定包含在当前业务 Trace TTFT 起点中；
- Trace API 当前未限制为管理员；
- errorMessage 仍可能带上游敏感细节；
- 应用日志仍有 Rewrite 原始问题、子问题、模型原始输出和异常内容的泄露点，
  所以“Trace 表不存正文”不等于全系统完成日志脱敏；
- Active Gateway 调用 orchestrator 的五参数 overload，而 `@RagTraceNode` 标在四参数
  overload 上；当前 Active 编排节点不会按预期被 AOP 记录，需要调整切点并补集成测试；
- 显式 `/stop` 直接操作底层 emitter/handle，未经过 trace-aware callback，
  RAG Run 可能留在 `RUNNING`；Agent adapter 的 `CANCELLED` 反而调用
  `onComplete`，可能记成 `SUCCESS`。Trace 需要独立的 `CANCELLED` 终态和统一终止入口。

---

## 12. 数据与存储边界

### 12.1 PostgreSQL

负责：

- 用户；
- 会话、消息、摘要、反馈；
- 知识库、文档、Chunk；
- 文档计划和执行记录；
- 意图树和术语映射；
- Trace Run/Node；
- 摄取 Pipeline、Task 和节点；
- pgvector 向量。

当前 schema 没有外键，完整性依赖服务层，优点是导入和软删除灵活，代价是必须做孤儿数据巡检。

### 12.2 Redis

当前混合承载：

- Sa-Token 会话；
- 分布式锁；
- 限流队列和 permit；
- 幂等状态；
- 取消标记和 Pub/Sub。

Docker 使用 `allkeys-lru`。这对纯缓存合理，但会驱逐登录、锁、取消等正确性状态，是生产风险。

改进：

- 正确性数据使用 `noeviction`；
- 缓存和协调状态拆分实例；
- 明确每类 key 的 TTL、容量和告警。

### 12.3 RocketMQ

负责文档耗时切片任务。当前单 NameServer、单 Broker、异步刷盘，适合资源受限演示，不是 HA。

### 12.4 RustFS

负责原始文件。数据库只保存 URL 和元数据，不把大文件塞入关系表。

当前 Compose 使用单节点 `1.0.0-alpha.72` 镜像。它适合个人项目验证，
不能当作成熟对象存储高可用方案。正式上线需要选择受支持稳定版本、
固定 image digest、配置冗余，并完成升级和恢复演练。

### 12.5 pgvector 与 Milvus

| 维度 | pgvector | Milvus |
| --- | --- | --- |
| 当前部署 | 是 | 否 |
| 运维复杂度 | 低 | 较高 |
| 与业务表事务 | 可共享 | 不可共享 |
| 小规模适合度 | 高 | 一般 |
| 大规模向量扩展 | 有上限 | 更强 |

项目保留抽象是为了演进，不代表两个后端同时在线。

---

## 13. 安全边界与上线红线

### 13.1 已有安全措施

- 所有业务接口默认要求登录；
- 用户管理接口显式要求 admin；
- 非 AUTO 执行模式要求 admin；
- 新密码使用 BCrypt；
- 登录时可把历史明文密码升级为 BCrypt；
- 生产 PostgreSQL 只发布到服务器 loopback；
- `.env` 被 Git 忽略，能够降低误提交概率，但这不等于完成 Secret Management；
- Agent Tool 有 allowlist；
- FullDocumentExpander 采用 fail-closed 归属校验；
- Eval 在当前 Docker 部署默认关闭；
- Demo Mode 会拦截除 `/auth/**` 外的大部分非 GET 请求，并额外拦截有副作用的
  Chat GET；它不是覆盖注册和认证写入的全局只读安全边界。

### 13.2 P0：后端 RBAC 未闭环

前端隐藏 `/admin` 不是安全边界。当前除用户 CRUD 等少数接口外，以下管理 API 缺少统一 `checkRole("admin")`：

- 知识库、文档、Chunk；
- 意图树；
- 摄取 Pipeline 和 Task；
- Dashboard；
- Trace；
- Settings；
- 示例问题；
- 术语映射。

开放注册用户可绕过前端直接请求这些接口。

`/rag/settings` 还会向普通登录用户暴露 provider、model、endpoint 等部署拓扑，
并返回 API key 的前后掩码片段。掩码不是授权；该接口必须限制为 admin，
且默认不应返回任何 key 片段。

修复：

1. 后端统一注解或拦截器保护管理路径；
2. 资源查询继续做对象级授权；
3. Controller 测试覆盖普通用户 403；
4. 前端权限仅作为体验优化。

正式环境还应关闭开放注册，或改为邀请、SSO/组织开通；管理员创建、管理员重置、
用户自助改密与注册必须复用同一套密码强度规则。

### 13.3 P0：首轮检索 ACL 缺失

当前 `RetrievalEngine` 不接收 principal，PG 检索主要按 collection 查询，全局通道枚举所有知识库。

只有全文扩展做了 owner/admin 校验。因此不能声称：

- 企业级多租户隔离；
- 所有检索阶段都经过 ACL；
- 普通用户无法召回受限 Chunk。

正确链路：

```text
User Principal
  -> 计算 allowedKbIds / allowedDocIds
  -> 下推到 Intent 和全局检索 SQL
  -> 召回后再次过滤
  -> Evidence 建账
  -> 全文扩展再次 fail-closed
```

必须做到“前过滤 + 后校验”，不能只在 Prompt 里告诉模型不要泄露。

### 13.4 P0：本地文件读取与 SSRF

可配置摄取接口允许 FILE/URL 来源，而当前：

- `LocalFileFetcher` 可读取用户提供的容器路径；
- 节点输出可能记录 rawBytesBase64/rawText；
- HTTP 拉取没有完整的 scheme、私网 IP、重定向和 DNS rebinding 防护。

如果再叠加后端 RBAC 缺失，会形成严重数据外泄。

上线前必须：

- 禁止 API 用户提交任意本地路径；
- 只接受 multipart 或受控对象 ID；
- URL 只允许 HTTPS 和域名白名单；
- 每次 DNS 和每一跳重定向都校验目标地址；
- 阻断 loopback、私网、链路本地和云元数据地址；
- 禁止原始文件内容进入可查询任务日志；
- 配置容器出站网络策略。

摄取还存在独立的内存耗尽风险：

- `/knowledge-base/{id}/docs/upload` 有上传并发限制，但通用
  `/ingestion/tasks/upload` 不经过该 Filter；
- multipart `getBytes()`、URL 无界响应、LocalFile/S3 `readAllBytes()` 会把完整内容
  读入堆；
- `NodeOutputExtractor` 再做 Base64/文本复制，输出截断发生在大对象已经分配之后；
- 在约 1.5 GB Java 堆上，多请求并发即可形成 OOM/DoS。

修复时要让所有摄取入口共享文件大小、并发和用户配额，做 `Content-Length`
预检与流式硬上限，使用流式解析/对象存储引用，并禁止 raw bytes 进入节点输出。
同时必须清查并清理历史 `t_ingestion_task_node.output_json` 中的
`rawBytesBase64/rawText`；若其中可能出现凭证，应按泄露事件轮换相关秘密，
而不是只修未来写入。

### 13.5 P0：取消接口 IDOR

当前 stop 只凭 taskId，没有 userId 所有权校验。Trace 又会暴露 taskId。

修复：

- 创建任务时保存 `taskId -> ownerUserId`；
- cancel 使用 Lua/事务原子校验 owner；
- 管理员可显式 override；
- Trace API 仅管理员可见；
- 对 taskId 使用不可预测 ID 仍不能替代授权。

### 13.6 管理员密码初始化

当前 `admin-bootstrap` 会把环境中的管理员密码直接写入数据库，再依赖首次登录升级 BCrypt。

风险：

- 首次登录前为明文；
- bootstrap 重跑可能覆盖已修改密码。

应改为：

- 部署前生成 BCrypt hash；
- 初始化 SQL 只写 hash；
- 仅在未初始化时执行；
- 密码重置后撤销旧会话。

Compose 通过环境变量注入秘密，拥有宿主机或 Docker 高权限的主体仍可读取。
正式环境需要 secret file/Vault/KMS、最小权限、定期轮换与提交前泄露扫描。
仓库中已有 credential-looking 测试 token，应移出版本库并轮换，文档和日志均不得打印其值。

### 13.7 SSE 与认证风险

- Chat 使用有副作用的 GET，问题会进入 URL/代理日志；
- 前端断线自动重试可能重复推理和计费；
- `@IdempotentSubmit` 只在 Controller 调用期间持 Redis 锁，方法返回后就释放，
  而 SSE 后台任务尚未结束，因此不能阻止断线重试产生新的任务、消息和费用；
- token 存 localStorage，需重视 XSS；
- CORS 当前是 `allowedOriginPatterns("*") + allowCredentials(true)`，生产必须改显式 allowlist；
- 登录/注册缺少速率限制和锁定；
- 用户自助改密和管理员重置密码后均未撤销已有 token/session；
- 管理操作尚无 append-only 安全审计，RAG Trace 不能代替审计日志。

推荐：

```text
POST /chat-tasks + Idempotency-Key
  -> 返回 taskId
GET /chat-tasks/{taskId}/events
  -> SSE 订阅
```

幂等键必须落到持久化 task/result 上：相同用户、相同 idempotency key
要复用同一任务，而不是只靠短时分布式锁。入口还应补 CSP、HSTS、
frame/referrer policy；管理员账号至少应有 MFA 或更强身份源。

### 13.8 Prompt Injection 与开发默认配置

检索到的文档、网页和 MCP 返回值都是不可信数据，不应获得系统指令优先级。
当前尚未形成完整的 Prompt Injection 防护；Agent Tool allowlist 主要保护
Agent 执行路径，RAG 的意图驱动 MCP 路径没有完全复用同一 policy。

正式方案需要：

- 用强分隔和角色消息明确“文档是数据，不是指令”；
- 工具权限在执行器层强制，不能只靠 Prompt；
- 对高风险内容、外链和数据外传做检测；
- 最终输出和工具参数做结构校验；
- 将 RAG MCP 与 Agent Tool 纳入统一认证、allowlist、审计和超时边界。

本地 `application.yaml` 还包含仅供开发的弱默认连接配置，Eval 默认值也与
Docker 部署不同。当前 Docker 会用环境变量覆盖并关闭 Eval，但正式部署应做到：
关键秘密缺失即启动失败，不允许悄悄回落到开发默认值。

### 13.9 本地连接生产数据库

当前本地应用通过 SSH 隧道访问生产 PostgreSQL：

```text
127.0.0.1:15432
  -> SSH jd-ecs
  -> production 127.0.0.1:5432
```

优点：

- 数据库不暴露公网；
- 利用 SSH key；
- 连接可审计和立即关闭。

风险：

- 本地测试会直接写生产；
- 清理脚本、集成测试和 schema 实验可能破坏生产；
- GUI 误操作风险高。

至少应使用只读数据库账号给 GUI，并建立独立 staging 数据库。不要把“SSH 安全”误解为“本地写生产是合理开发流程”。

---

## 14. 一致性、幂等与失败语义

### 14.1 降级矩阵

| 失败点 | 当前行为 | 取舍 |
| --- | --- | --- |
| 记忆摘要加载失败 | 跳过摘要 | 可用性优先 |
| 历史加载失败 | 空历史继续 | 可能丢多轮语义 |
| 单检索通道失败 | 空结果继续其他通道 | 避免全链失败，但需标记 degraded |
| MCP 调用失败 | error result/空上下文 | 模型可见工具失败 |
| Rerank 关闭 | 直接使用去重结果 | 当前部署状态 |
| Agent 异常或不可交付 | 回退 RAG | 兼容层不降低可用性 |
| Agentic 失败 | 回退首轮结果 | 增强层不降低基线 |
| Trace 写入失败 | 记录 warn，业务继续 | 观测不能阻断业务 |
| assistant 落库失败 | 回答仍可返回 | 体验优先，会话可能缺记录 |
| 文档处理失败 | 标记 FAILED 和日志 | 当前不会自动 MQ 重试 |

可用性优先的代价是“静默质量退化”。因此必须让 Trace 明确记录 fallback、degraded channel 和缺失上下文，不能只返回 200。

### 14.2 幂等不是“catch 异常”

关键场景：

- 上传对象成功但 DB 失败；
- MQ 消息重复；
- SSE 断线后重试；
- 模型工具调用成功但响应丢失；
- 文档更新并发执行；
- 定时任务与人工重切片同时发生。

正确幂等需要：

- 稳定业务键；
- 状态条件更新；
- 版本号；
- 结果复用；
- 重复请求返回同一结果；
- 对账和补偿。

### 14.3 对象存储与跨存储一致性

数据库事务不能覆盖 RustFS：

- 创建知识库时，数据库 insert 后调用 `createBucket`；如果后续 vector ensure
  或事务提交失败，数据库会回滚但 bucket 可能遗留；
- 重试时如果 `BucketAlreadyOwned` 被当作错误，流程会进入“孤儿资源导致不可重试”；
- 上传对象成功、数据库写入失败会留下孤儿对象；
- 删除对象是 best-effort，失败后数据库仍可能提交。

正确做法是幂等 `ensureBucket`、对象/数据库状态机、outbox/saga、补偿任务和定期对账。
同理，当前 pgvector 与 Chunk 可共享 PostgreSQL 事务；切换 Milvus 后只能做最终一致，
不能把方法名里的 `Atomically` 当成跨存储 ACID 证明。

### 14.4 软删除与唯一约束

当前多个表使用软删除，但唯一键未全部纳入可复用语义。删除后的 username、conversationId、collectionName 可能无法再次使用，`UNIQUE(name, deleted)` 也可能在重复删除周期中冲突。

面试回答应说明：

- 软删除不只是加 `deleted` 字段；
- 唯一键、查询条件、恢复、归档和数据清理必须一起设计；
- PostgreSQL 可采用部分唯一索引 `WHERE deleted = 0`。

`t_message` 还存在字段相同的重复索引，会增加写放大和维护成本。
删除前应结合 `pg_stat_user_indexes` 验证真实使用情况。

### 14.5 数据库迁移

当前使用初始化 schema 和人工 upgrade SQL，没有 Flyway/Liquibase 与 schema version 表。

正式演进需要：

- 每个版本一个不可变 migration；
- migration history；
- 幂等或明确前置版本；
- DDL 事务边界；
- 向前兼容的 expand/contract；
- 备份与回滚演练。

---

## 15. 测试与评测

### 15.1 两类测试必须分开

默认离线测试：

```powershell
mvn test
```

- 不依赖 Redis、PostgreSQL、Milvus 和外部模型；
- 必须作为快速反馈稳定通过。

集成测试：

```powershell
mvn test -Pintegration
```

- 需要明确的 Docker 环境、数据库和模型配置；
- 失败不能被当成普通单元测试失败；
- 也不能通过吞异常把构建“刷绿”。

当前 integration profile 尚未形成可重复的一键绿灯基线：

- 没有用 Testcontainers 固化 PostgreSQL、Redis、Milvus 等依赖；
- 没有仓库内 CI 主流程持续执行；
- `PgVectorStoreServiceTest` 的历史 SQL 字段与当前向量表 schema 存在漂移；
- Docker 镜像构建使用 `-DskipTests`。

因此默认单测通过不能外推为“真实基础设施和端到端链路全部通过”，
镜像构建成功也不能替代测试报告。

### 15.2 当前验证快照

2026-07-26 在当前工作树执行默认测试：

- `infra-ai`：5 项通过；
- `bootstrap`：158 项通过；
- `mcp-server`：当前没有测试；
- 合计 163 项，0 failure、0 error、0 skipped，Reactor `BUILD SUCCESS`。

受限执行环境可能无权清理系统 `%TEMP%` 中的 JUnit 临时目录，表现为
`Failed to close extension context` 和 `AccessDeniedException`。这不是业务断言失败。
在此类环境中可把临时目录显式放到工作区后重跑：

```powershell
New-Item -ItemType Directory -Force "$PWD\target\junit-tmp" | Out-Null
$env:TEMP = "$PWD\target\junit-tmp"
$env:TMP = "$PWD\target\junit-tmp"
$env:MAVEN_OPTS = "-Djava.io.tmpdir=$PWD\target\junit-tmp"
mvn test
```

这个结果只证明默认离线测试集通过，不代表 PostgreSQL、Redis、RocketMQ、对象存储、
真实模型和浏览器 SSE 的端到端链路已经全部验证。

### 15.3 测试重点

已有较好覆盖：

- Agent domain、Runner、Handler、恢复与策略；
- Agentic Retrieval 领域模型、Parser、预算和回退；
- 模型路由；
- Spring AI Prompt 映射和同步空响应；
- 部分 RAG Service。

仍需加强：

- 真实 PostgreSQL/pgvector；
- RocketMQ 重投与幂等；
- 分布式限流 lease；
- SSE 断线、重连、取消；
- 后端 RBAC 和资源 ACL；
- 对象存储与数据库补偿；
- schema migration；
- 完整回答引用正确性。

### 15.4 RAG 评测维度

至少分四层：

| 层 | 指标 |
| --- | --- |
| Rewrite/Route | 子问题覆盖、复杂度误路由率 |
| Retrieval | Recall@K、MRR、nDCG、噪声率 |
| Answer | Correctness、Faithfulness、拒答正确率 |
| Citation | Citation recall、precision、claim support |
| Runtime | TTFT、P95、调用次数、Token、费用、fallback rate |

不能只看“回答看起来不错”。

### 15.5 如何解释 100% Recall

标准回答：

> 这个 100% 来自 25 题、9 篇文档的小型固定集，Top5 很容易覆盖目标文档，已经发生指标饱和。它只能证明评测脚本和当前检索链路可重复，不能外推生产准确率。更重要的反例是无答案问题空召回率为 0%，说明系统会过度召回。下一步应该扩大难负例、相似版本和权限样本，并下沉到 Chunk、事实和最终答案指标。

---

## 16. 部署拓扑与容量边界

### 16.1 单机 Compose

```text
Browser
  -> Nginx :80
       -> React static files
       -> /api/* proxy app:9090

app
  -> PostgreSQL 17 + pgvector
  -> Redis
  -> RocketMQ NameServer + Broker
  -> RustFS
  -> MCP Server
  -> SiliconFlow
```

### 16.2 2 核 8 GB 取舍

- Java 最大堆约 1536 MB；
- Hikari 最大连接数 5；
- Redis 数据内存约 192 MB；
- `/knowledge-base/{id}/docs/upload` 路径并发 1；通用 ingestion upload 不命中该 Filter；
- Chat 配置并发 2；
- 不启动 Milvus；
- 不启动本地 LLM；
- 不启动 RocketMQ Dashboard；
- 单 NameServer、单 Broker；
- Rerank 关闭。

这是容量约束下的服务收敛，不是通用最佳实践。

### 16.3 当前不是 HA

缺少：

- 多副本应用和滚动升级；
- PostgreSQL 主从/PITR；
- Redis HA；
- RocketMQ 多 Broker 同步复制；
- 对象存储冗余；
- TLS/WAF；
- 自动 migration；
- Prometheus/Grafana/告警；
- 定期备份和恢复演练。

准确口径：

> 当前是可部署的单机演示和灰度基线，不是高可用生产集群。

### 16.4 入口和健康检查缺口

- PostgreSQL 只绑定 loopback，这一点正确；
- app 9090 默认仍绑定 `0.0.0.0`，可能绕过 Nginx；
- Nginx 与 app 没有仓库内 TLS；
- Compose 默认单 bridge 网络，frontend/app/data tier 没有网络隔离；
- 前端有 healthcheck；
- PostgreSQL/Redis/NameServer 有基础检查；
- app、MCP、RustFS 缺少能区分 liveness/readiness 的有效检查；
- RustFS、MCP、Broker 等多处依赖只到 `service_started`，存在启动竞态。

正式方案：

- 只公开 Nginx；
- app 绑定 internal application network，数据组件使用更窄的 internal data network；
- TLS 在统一入口终止；
- liveness 只判断进程；
- readiness 校验 DB、Redis 和必要配置，但不要求外部模型永久在线；
- 模型状态通过独立 dependency health 暴露；
- 所有外部依赖启动均配 readiness、应用侧重试和指数 backoff。

---

## 17. P0～P3 整改路线

### P0：安全与数据边界

1. 后端统一 RBAC，保护全部管理 API；
2. 首轮、全局和补检索接入资源 ACL；
3. 移除任意本地路径读取，封堵 SSRF，统一上传大小/并发并清理历史 raw output；
4. stop 校验 task owner，浏览器断链取消活跃上游，Agentic 取消后禁止重新生成；
5. Trace/settings 仅管理员可见，补日志脱敏、Prompt Injection 和 MCP 工具策略；
6. 管理员初始化只写 BCrypt hash，改密撤销旧会话，统一密码规则；
7. 处理并轮换 credential-looking 测试 token，关键秘密缺失时启动失败；
8. 只公开 Nginx，补 TLS 和显式 CORS allowlist；
9. GUI 使用远程数据库只读账号，建立独立 staging。

### P1：正确性与可验证性

1. SSE 改为 POST 幂等创建任务 + GET 订阅；
2. permit 由流终态释放并续租；
3. 排队请求与 Shadow 显式携带 principal snapshot，reject 复用并注销原 task，
   补跨用户隔离、Shadow/Active 同构与单 meta 契约测试；
4. MQ 消费幂等、重试、DLQ 和超时任务恢复；
5. 对象存储幂等建桶、outbox/补偿和孤儿清理；
6. Redis 正确性状态改 `noeviction` 或与缓存拆分；
7. 引入 Flyway/Liquibase；
8. 增加网络分层、readiness、备份恢复和依赖重试；
9. 扩展到至少 50 条真实评测，补拒答、ACL、冲突和引用；
10. 完成前端引用卡片及最终文本引用校验。

### P2：质量与迁移

1. 拆分顶层 `ExecutionMode` 与 RAG 内部 `RetrievalStrategy`；
2. 实现 BM25/ES 通道和分数融合；
3. 接入真实可用 Rerank Provider；
4. 修正 Chunk 到具体 task/intent 的 provenance；
5. 优化复杂度路由和低延迟 Evaluator/Planner；
6. 接入真实 Token、调用数、费用和 Agentic 指标；
7. 将 Spring AI 同步 Chat 接入可配置切流；
8. 再迁移 Embedding 和 Streaming；
9. MCP 增加认证、重连和工具冲突治理。

### P3：规模化与高可用

1. 多副本应用和统一 Observability；
2. PostgreSQL/Redis/RocketMQ/Object Storage HA；
3. 多租户组织权限模型；
4. 索引分片、冷热分层和大规模向量后端；
5. CI/CD、灰度发布、自动回滚；
6. 容量压测、故障演练和成本治理。

---

## 18. 面试回答框架

### 18.1 五段式

任何工程问题优先按以下结构：

1. **结论**：一句话直接回答；
2. **依据**：具体类、状态或调用链；
3. **取舍**：为什么没选另一个方案；
4. **限制**：当前哪里还不完整；
5. **下一步**：可执行改造。

示例：

> 当前部署选择 pgvector，不是因为它在所有规模都优于 Milvus，而是目标机器只有 2 核 8 GB。pgvector 减少了一个独立服务，并让 Chunk 和向量共享 PostgreSQL 事务。代价是大规模向量扩展能力较弱，所以代码保留 VectorStore 抽象。未来切 Milvus 时，我会用 outbox、版本号和对账，不会继续宣称跨存储强事务。

### 18.2 被指出问题时

不要强行辩解。使用：

```text
你指出的是当前实现的真实边界。
现状是……
当时这样做的约束是……
风险是……
我会按……修复，并通过……验证。
```

能准确识别债务，比为了“项目完整”否认代码事实更有说服力。

---

## 19. 高频面试题与参考回答

时间有限时先背 12 题：Q1、Q4、Q7、Q14、Q17、Q24、Q29、Q34、
Q35、Q39、Q41、Q44。它们覆盖项目定位、架构边界、一致性、效果、
框架迁移、并发取消、安全与生产口径。其余题用于面试官继续下钻时展开。

### 19.1 项目与架构

#### Q1：项目最核心的技术亮点是什么？

> 不是单个模型调用，而是完整的数据和运行闭环：摄取状态、异步切片、向量替换、多范围检索、流式生命周期、记忆、Trace、取消、模型降级和 Agentic 灰度。亮点在边界和失败治理，而不是抽象数量。

#### Q2：为什么不用现成 SaaS 或直接查 Wiki？

> 企业场景还需要私有数据、权限、版本、增量更新、来源引用、审计、评测和业务工具组合。Wiki 搜索解决单站点人工查找，不能替代这些治理能力。

#### Q3：为什么 RAG 和 Agent 都存在？

> 稳定知识问答优先使用确定性 Pipeline；只有步骤或工具选择不能预先确定时才需要 Agent。Agent 引入更高成本和不确定性，所以不能把所有请求都改成 Agent。

#### Q4：Agent、RAG、Agentic Retrieval 是三个模式吗？

> 不是。RAG 和 Agent 是顶层执行范式，Agentic Retrieval 是 RAG 内部检索增强。当前枚举把两个维度混在一起，是已识别的建模债务。

#### Q5：为什么自己写 RAG 编排，不直接用 Spring AI Advisor？

> Spring AI 适合模型协议和通用扩展点，但知识库路由、摄取状态、Evidence、ACL、引用、灰度和业务 Trace 是项目差异化能力。框架接管协议层，自研保留业务层。

### 19.2 摄取与数据一致性

#### Q6：为什么文档切片要异步？

> 解析、Embedding 和索引耗时且依赖外部模型。如果同步占用上传请求，会造成超时和资源占用，所以知识库文档主链使用 MQ，能够记录 RUNNING/FAILED 和阶段日志。但通用 Ingestion Task 当前仍同步驱动 Pipeline；消费者失败也会 catch 后 ACK，自动重试/DLQ 尚未闭环，现阶段只能说有人工重启/重放基础。

#### Q7：RocketMQ 事务消息解决了什么？

> 它解决“数据库状态与消息可见性”的一致性：只有本地事务把文档置为 RUNNING 后消息才提交，Broker 回查也读该状态。但消费仍至少一次，必须另做幂等。

#### Q8：能保证消息 exactly-once 吗？

> 不能。事务消息不等于消费 exactly-once。当前消费者幂等还不完整，正式方案需要 eventId、条件 claim、状态版本、重试/DLQ 和对账。

#### Q9：文档、Chunk 和向量是一个事务吗？

> 当前 pgvector 部署下可以，因为都在 PostgreSQL。切 Milvus 后不可以，需要 outbox 和补偿。

#### Q10：为什么不直接把文件存数据库？

> 大对象会放大备份、WAL 和数据库 IO。RustFS 保存原始对象，PostgreSQL 保存元数据和状态，向量表保存可检索表示。

#### Q11：摄取 Pipeline 是 DAG 吗？

> 它是带条件和环检测的可配置链，节点只有单 next 指针，不支持通用 DAG 的并行和汇合。这样足够覆盖当前流程，也避免过早复杂化。

#### Q12：文档更新如何避免旧内容继续被检索？

> 当前主流程在事务中删除旧 Chunk/向量并写入新版本，再更新文档状态。远程来源可通过 ETag、Last-Modified 和 hash 判断是否变化。跨对象存储或 Milvus 时仍需版本化和对账。

#### Q13：Wiki 页面怎么做知识库？

> 主链仍是 Fetch、Normalize、Chunk、Embed、Index，但必须保留页面 URL、revision、时间和 license，按标题章节切片，并用 revision 做增量更新。

### 19.3 检索与效果

#### Q14：多通道具体是什么？

> 当前落地的是意图定向向量通道和全局向量兜底通道。它们按条件并行，之后去重和可选 Rerank。BM25/ES 尚未实现。

#### Q15：为什么还需要全局检索？

> 意图分类可能低置信或漏判，全局检索提供 recall 兜底。代价是扫描更多 collection、噪声和 ACL 风险，所以必须设阈值并下推允许访问范围。

#### Q16：多通道分数怎么融合？

> 当前主要是合并、按 Chunk 去重、保留高分，再可选 Rerank；没有完整归一化或 RRF。这是后续混合检索要补的。

#### Q17：Recall@5 100% 是否说明效果很好？

> 不能。数据只有 25 题、9 篇文档，文档级指标饱和；无答案问题空召回率还是 0%。需要更难负例、Chunk/事实级指标和最终答案评测。

#### Q18：为什么无答案还会召回？

> 向量检索总能返回“最相似”结果，相似不等于足以回答。需要绝对阈值、证据充分性判断、负例训练集和明确拒答策略。

#### Q19：Rerank 上线了吗？

> 代码有处理链和客户端抽象，但当前 SiliconFlow Docker 配置关闭 Rerank，生产不能声称已启用精排。

### 19.4 Agentic Retrieval

#### Q20：为什么需要 Agentic Retrieval？

> 固定 Single Pass 无法根据首轮证据缺口动态补查。Agentic Retrieval 增加“评估—规划—补检索”，但只对复杂问题有潜在价值。

#### Q21：为什么最多一次补检索？

> 目标机器资源小，外部模型又慢。两轮足以覆盖“发现缺口后定向补一次”的主要路径，并把成本和延迟设成硬上限。放开前必须证明边际收益。

#### Q22：如何防止 Planner 越权？

> Planner 只应输出已知 taskId 和 query；Parser 拒绝未知/重复 task，执行模型转换复用原 NodeScore。额外的 SQL/KB 字段当前会被 Gson 忽略而不会进入执行，但还没做到 unknown-field reject。更重要的是首轮 ACL 尚未闭环，所以这里只能说“不扩张既有路由”，不能说已完成授权保护。

#### Q23：Shadow 为什么安全？

> Shadow 的功能隔离是：始终用首轮上下文回答，Agentic 异常和队列拒绝不改变本次结果。但它仍消耗模型额度、费用、线程和网络，也可能争抢供应商配额。当前 Shadow 还没有携带 principal，授权全文扩展会 fail-closed，所以与 Active 不完全同构；正式灰度需要补 principal snapshot、独立 bulkhead、配额和成本指标。

#### Q24：Agentic 到底提升了多少？

> 当前 5 题对比没有召回增益，平均多约 10.6 秒，还有 20% 回退，所以不能宣称提升。已经证明的是可控闭环和回退机制。

#### Q25：为什么最初 8 秒后来改成 120 秒？

> 真实供应商下 8 秒导致评估 100% 超时。调整到 Evaluator 45 秒、Planner 30 秒、总 120 秒是为了让链路可验证，不代表 120 秒是可接受 SLA。下一步应换低延迟模型、减少 Prompt 和提高路由精度。

#### Q26：EvidenceLedger 的价值是什么？

> 它把证据、任务状态、去重键和检索轮次显式化，避免只把一大段文本交给模型。它也让不足、冲突、无新增和预算耗尽可以被观测。

#### Q27：为什么不复用 AgentState？

> AgentState 表示 Plan-Act 动作历史；EvidenceLedger 表示检索任务的事实覆盖、冲突和来源。生命周期和停止条件不同，强行复用会混合两套预算。

#### Q28：引用能防幻觉吗？

> 不能。当前只能保证注入 Prompt 的 citation catalog 来自真实 Evidence；模型仍可能输出不存在的编号，也没有逐句验证 Evidence 是否支持结论。它提升可追踪性，不是事实证明器。

### 19.5 模型、Spring AI 与 MCP

#### Q29：项目到底有没有用 Spring AI？

> 引入了 1.1.8 依赖并完成 Prompt Mapper 和同步 Invoker seam，但未接入生产 Bean。当前主链仍是自研 LLMService、OkHttp 和 SSE。

#### Q30：为什么渐进迁移而不是重写？

> 先保持业务端口和评测基线不变，只替换协议层，能按 Chat、Embedding、Streaming 分别回滚。一次重写很难判断质量变化来自框架、Prompt 还是检索。

#### Q31：模型熔断怎么做？

> 按模型 ID 记录连续失败，达到阈值进入 OPEN，到期允许单个 HALF_OPEN 探测。成功关闭，失败重开。状态目前在单应用内存中，多副本时需要共享或接受实例级熔断。

#### Q32：流式模型为什么不能随时 fallback？

> 首包前可以切，因为用户还没看到内容；首包后切换可能重复前缀、语义断裂或形成两套答案，所以后续错误只能终止或由上层重试整个任务。

#### Q33：MCP 是真实企业系统吗？

> 注册、发现和调用链路已实现，但 bundled 天气、销售和工单是演示数据。服务间认证、重连和工具冲突治理尚未完成。

### 19.6 并发、SSE 与可观测性

#### Q34：聊天并发 2 是严格的吗？

> 当前不是完整流生命周期的严格上限。Redis 排队和 semaphore 主要覆盖到流启动附近，lease 还短于最长请求，需要终态释放和 watchdog 续租。

#### Q35：客户端断开会停止模型吗？

> 排队阶段会取消 Ticket，但 Ticket 进入 GRANTED 后，emitter 断链目前没有继续调用 task manager 取消活跃上游，因此模型、落库和计费可能继续。显式 stop 有 Redis bucket、Pub/Sub 和 cancellation handle，但还缺 owner 校验。正确方案是让 emitter 终态统一触发 owner-aware task cancel，并用端到端测试验证。

#### Q36：为什么 SSE 用 GET 有问题？

> Chat 会创建任务、写消息并产生模型费用，是有副作用操作。GET 断线自动重试可能重复执行，问题也会进入 URL 日志。应 POST 幂等创建任务，再 GET 订阅事件。

#### Q37：Trace 为什么不记录完整 Prompt？

> Trace 数据表不保存正文，是为了避免企业文档在观测库二次扩散；主要记录节点、状态、耗时和问题长度。但应用日志仍有原始问题、子问题和模型原始输出泄露点，所以日志脱敏没有闭环。复现能力应通过受控采样、脱敏调试模式和访问审计补充。

#### Q38：TTFT 包含排队时间吗？

> 当前 Trace 在获得限流资格后进入业务包装，不能保证包含从 HTTP 入站开始的全部排队时间。应增加 ingress timestamp 和 queue wait 指标。

### 19.7 权限与生产

#### Q39：企业 ACL 在哪里？

> 当前只有全文扩展有局部 owner/admin 校验，首轮检索、全局检索和管理 API 尚未完整接入资源 ACL，这是上线前 P0，不能包装成已完成。

#### Q40：为什么前端隐藏管理员菜单还不够？

> 前端可以被绕过，授权必须由后端强制。当前多个管理 Controller 只有登录校验，需要统一 RBAC 和资源级策略。

#### Q41：这个项目生产级吗？

> 它是 2 核 8 GB 单机可部署灰度基线，不是 HA。缺少 TLS、自动 migration、备份恢复、多副本和完整告警。

#### Q42：为什么当前部署选 pgvector？

> 资源限制下减少组件，并获得 Chunk/向量同库事务。规模扩大时可切 Milvus，但要引入跨存储一致性方案。

#### Q43：本地为什么连生产库？

> 当前为了统一数据通过 SSH loopback 隧道复用生产 DB，数据库不暴露公网。但这是高风险临时开发方式，测试应使用 staging，GUI 至少用只读账号。

#### Q44：最大的安全风险是什么？

> 当前优先级最高的是后端 RBAC、首轮检索 ACL、任意本地文件读取/SSRF、取消接口所有权和明文管理员初始化。

### 19.8 反思题

#### Q45：如果重新做一次，最早会改变什么？

> 我会更早确立项目边界：标准框架负责模型协议，自研集中在摄取、检索、权限和评测；同时先做 ACL 和固定评测集，再扩展 Agentic。这样能避免功能先增长、证据和权限后补。

#### Q46：当前最值得保留的设计是什么？

> 受预算约束的增强层和明确回退：Agent 失败回 RAG、Agentic 失败回首轮、模型首包前可切候选。它让实验能力不会轻易拖垮基线。

#### Q47：当前最应该删除或隔离的是什么？

> ragent 中的通用 Agent 兼容代码和无关演示 MCP 应逐步隔离；同时把 AGENTIC 从顶层执行枚举拆出去，恢复业务边界。

### 19.9 代码深挖题

#### Q48：用了 TTL 线程池，为什么还可能串用户？

> TTL 只在“向已包装 executor 提交任务”时捕获上下文。排队限流器的 scheduler 没包装，它再提交 grant/timeout 时可能捕获 scheduler 线程上的错误值。身份不能依赖 ambient ThreadLocal，应把 immutable principal 放进排队请求，执行前 set、finally clear，并做双用户并发测试。

#### Q49：上传并发已经限制为 1，为什么还可能 OOM？

> 这个 Filter 只覆盖知识库 docs/upload，通用 ingestion upload 能绕过；而 `getBytes/readAllBytes/Base64` 会在堆里形成多份完整内容。正确方案是统一入口配额、流式硬上限和对象引用，不把 raw bytes 写入任务输出。

#### Q50：一个用户问题最多会放大成多少外部调用？

> 当前没有完整的请求级硬上限。Rewrite 一次、每个子问题一次 Intent、可选歧义判断、每个子问题和候选库的检索 fan-out、Agentic 最多三次额外模型判断、最后一次生成。下一步应限制子问题、候选库、模型调用、Token、并行任务和各阶段 deadline。

#### Q51：为什么选择当前 Chunk 大小和 overlap？

> 项目既有固定切片也有结构感知切片，但当前参数没有被充分评测证明最优。我会按标题/段落边界保持事实完整，再联合比较 Recall、噪声、最终答案忠实度和 Token 成本，而不是只凭经验调一个数字。

#### Q52：Embedding 模型或维度改变怎么办？为什么用 HNSW？

> 当前 pgvector schema 固定 `vector(1536)` 并使用 HNSW cosine。不同模型和版本的向量不能直接混检；应记录 embedding model/version，离线建立新索引，双读评测后切换。HNSW 适合当前读多写少的近似检索，但现有数据太小，还不能声称 `m/ef_construction/ef_search` 已完成容量调优。

#### Q53：全局检索如何扩展到一万个知识库？

> 当前枚举 collection 的 fan-out 会随知识库数增长，不能直接扩展。要先按 tenant/ACL scope 和意图选候选库，再使用统一索引或分区、分层召回和并发预算，必要时迁移专用检索后端。

#### Q54：如何防知识库文档中的 Prompt Injection？

> 文档和 MCP 返回只能作为数据，不能覆盖系统指令。需要角色隔离和强分隔、工具权限在执行器层校验、高风险内容检测、输出结构校验与数据外传审计。当前项目还没有完整闭环，所以这是 P0，而不是已完成亮点。

#### Q55：2 核 8 GB 能承受多少 QPS？

> 当前只有资源收敛和功能验证，没有足够压测数据，不能报一个生产 QPS。应分别测 queue wait、TTFT、完整流并发、检索 fan-out、取消后资源泄漏、供应商限额和单请求成本，再给容量结论。

#### Q56：自定义 Trace 与 OpenTelemetry 有什么区别？

> 当前 Trace 面向 RAG 业务节点和数据库查询，适合解释一次问答经过哪些阶段；它不是标准分布式追踪，缺少跨服务 context、标准 exporter、采样和 retention 治理。演进方向是保留业务语义，同时映射到 OpenTelemetry span/metric。

#### Q57：为什么不用 `ForkJoinPool.commonPool`？

> Intent、Retrieval、MCP 和 Shadow 的依赖、超时与负载不同，应使用独立有界线程池做 bulkhead。当前仍有嵌套 Future、无阶段 deadline 的 `join()` 和 CallerRuns 反压传播风险；线程池隔离必须和请求预算、拒绝策略、上下文清理一起设计，不能只换一个 executor 名称。

### 19.10 三层追问演练

练习时让同伴连续追问，不要只背第一句。

**链一：Agentic 效果**

```text
为什么做 Agentic？
-> Single Pass 不能按证据缺口动态补查。

提升了多少？
-> 5 题来源召回没有提升，平均额外约 10.6s，不能宣称有效果增益。

那为什么保留？
-> 保留的是 OFF/SHADOW/ACTIVE、预算、回退和评测能力；默认 OFF，
   等困难集证明净收益后再开。
```

**链二：消息一致性**

```text
RocketMQ 事务消息是否 exactly-once？
-> 只解决本地状态与消息提交，不解决消费 exactly-once。

重复消息怎么办？
-> 当前消费者幂等未闭环；需要 eventId/version、条件 claim、结果复用、DLQ。

切 Milvus 还能事务吗？
-> 不能；使用 outbox、版本化索引、补偿和对账。
```

**链三：企业权限**

```text
有管理员页面，是否企业级权限完成？
-> 前端不是边界；后端多个管理 API 和首轮检索 ACL 尚未闭环。

最坏结果是什么？
-> 普通登录用户可绕过 UI 管理资源，并可能召回不属于自己的 Chunk。

怎么修？
-> 统一 RBAC；allowed scope 下推召回前；召回后和全文扩展再次 fail-closed；
   普通用户 403/不召回测试。
```

**链四：并发与取消**

```text
并发配置 2 是否严格限制两个活跃流？
-> 不是，当前 permit 主要覆盖流启动，lease 也可能早于请求结束。

浏览器关掉会停模型吗？
-> GRANTED 后 emitter 断链尚未联动 task manager，可能继续推理和计费。

怎么修？
-> POST 幂等建任务；permit 终态释放并续租；emitter 终态 owner-aware cancel；
   压测活跃流、断链泄漏和 TTFT。
```

---

## 20. 扩容设计题

### 20.1 如果文档增长到百万级

演进顺序：

1. 引入 tenant/kb/doc ACL 下推；
2. 批量 Embedding 与摄取任务分区；
3. 文档和索引版本化；
4. BM25 + dense hybrid；
5. 热门 Query/Embedding 缓存；
6. pgvector 分区、索引调优和只读副本；
7. 达到容量边界后迁移 Milvus/OpenSearch；
8. outbox + reconciliation 保证跨存储最终一致；
9. 离线重建新 collection 后原子切别名。

### 20.2 如果问答 QPS 增加

1. 拆 ingress、排队、检索、模型阶段指标；
2. 应用无状态多副本；
3. Redis 协调数据独立实例；
4. 租约续期和终态释放；
5. 模型限流按 provider/model 分池；
6. Rewrite/Intent 结果缓存；
7. Embedding Query cache；
8. 流式任务状态外置；
9. 熔断和 fallback 状态共享或明确实例级；
10. 压测 TTFT、P95、取消泄漏和成本。

### 20.3 如果引入多租户

必须从数据模型开始：

- 所有 KB/doc/chunk/vector 带 tenantId；
- 唯一键包含 tenant；
- token 解析得到 tenant/principal；
- allowed scope 下推 SQL/向量过滤；
- 对象存储路径和 bucket 隔离；
- Trace、评测、缓存 key、MQ event 都带 tenant；
- 管理员分 platform admin 与 tenant admin；
- 全局检索不得枚举其他租户 collection。

不要只在 Controller 上加一个 tenantId 参数。

---

## 21. 代码阅读地图

### 21.1 问答入口

- `bootstrap/.../rag/controller/RAGChatController.java`
- `bootstrap/.../rag/service/impl/RAGChatServiceImpl.java`
- `bootstrap/.../rag/service/pipeline/StreamChatPipeline.java`
- `bootstrap/.../rag/service/handler/StreamChatEventHandler.java`

### 21.2 检索

- `bootstrap/.../rag/core/retrieve/RetrievalEngine.java`
- `bootstrap/.../rag/core/retrieve/MultiChannelRetrievalEngine.java`
- `bootstrap/.../rag/core/retrieve/channel/IntentDirectedSearchChannel.java`
- `bootstrap/.../rag/core/retrieve/channel/VectorGlobalSearchChannel.java`
- `bootstrap/.../rag/core/retrieve/postprocessor/*`

### 21.3 Agentic Retrieval

- `bootstrap/.../rag/core/agentic/AgenticRetrievalGateway.java`
- `bootstrap/.../rag/core/agentic/DefaultAgenticRetrievalOrchestrator.java`
- `bootstrap/.../rag/core/agentic/EvidenceLedger.java`
- `bootstrap/.../rag/core/agentic/LlmEvidenceEvaluator.java`
- `bootstrap/.../rag/core/agentic/LlmRetrievalTaskPlanner.java`
- `bootstrap/.../rag/core/agentic/FullDocumentExpander.java`

### 21.4 摄取

- `bootstrap/.../knowledge/service/impl/KnowledgeDocumentServiceImpl.java`
- `bootstrap/.../knowledge/mq/KnowledgeDocumentChunkConsumer.java`
- `bootstrap/.../knowledge/mq/KnowledgeDocumentChunkTransactionChecker.java`
- `bootstrap/.../ingestion/engine/IngestionEngine.java`
- `bootstrap/.../ingestion/node/*`

### 21.5 模型

- `infra-ai/.../chat/LLMService.java`
- `infra-ai/.../chat/RoutingLLMService.java`
- `infra-ai/.../model/ModelSelector.java`
- `infra-ai/.../model/ModelRoutingExecutor.java`
- `infra-ai/.../model/ModelHealthStore.java`
- `infra-ai/.../springai/*`

### 21.6 运行治理

- `bootstrap/.../rag/service/ratelimit/ChatQueueLimiter.java`
- `bootstrap/.../rag/service/ratelimit/FairDistributedRateLimiter.java`
- `bootstrap/.../rag/service/handler/StreamTaskManager.java`
- `bootstrap/.../rag/trace/StreamChatTraceRunner.java`
- `deploy/compose.yaml`
- `deploy/application-docker.yaml`

---

## 22. 面试禁止夸大清单

不要说：

- “项目基于 Spring AI 实现。”
- “已经完成企业级 ACL 和多租户隔离。”
- “多通道包含 BM25、ES、向量和混合检索。”
- “Agentic Retrieval 显著提升准确率。”
- “实现了可无限自主循环的通用 Agent。”
- “引用保证所有事实正确。”
- “Milvus 与 PostgreSQL 是同一个原子事务。”
- “自研了通用 DAG 编排平台。”
- “接入了多个真实企业 MCP 系统。”
- “pgvector 和 Milvus 在生产同时使用。”
- “聊天并发严格限制为两个完整流。”
- “SSE 断线重试完全幂等。”
- “当前 Compose 是高可用生产架构。”
- “所有管理接口都有后端管理员鉴权。”
- “所有测试和端到端场景已经完整覆盖。”

可以说：

- “完成了可部署的单机 RAG 工程闭环。”
- “实现了多范围向量召回和可扩展的通道框架。”
- “建立了 Agentic Retrieval 的预算、灰度、回退和评测机制。”
- “小规模评测没有证明 Agentic 收益，所以默认关闭。”
- “Spring AI 完成了第一阶段适配 seam，生产尚未切流。”
- “当前 pgvector 部署下 Chunk 和向量可共享数据库事务。”
- “权限、SSE 幂等和高可用仍有清晰的 P0～P3 路线。”

---

## 23. 最终速记卡

```text
定位：
企业知识库 RAG 工程，不是通用 Agent，不是 HA 商用平台。

两条顶层链：
RAG / Agent；Agentic Retrieval 属于 RAG。

摄取：
RustFS -> DB PENDING -> RocketMQ Tx -> Parse -> Chunk -> Embed
-> PG Chunk + pgvector -> SUCCESS/FAILED。

检索：
Rewrite -> Intent -> Intent Directed + Global Vector Fallback
-> Dedup -> optional Rerank -> Prompt。

Agentic：
First Pass -> Evidence Evaluate -> at most one Follow-up
-> Budget/Deadline/Cancel/Dedup -> Fallback。

模型：
当前自研 LLMService + Routing + Circuit Breaker；
Spring AI 只完成非生产 seam。

真实评测：
25 题小集 Recall@5 100% 但无答案空召回 0%；
5 题 Agentic 无召回增益，平均额外约 10.6s，默认 OFF。

当前部署：
2C8G 单机，pgvector，Rerank off，不是 HA。

测试：
2026-07-26 默认离线测试 163 项通过；集成链不是一键绿灯基线。

最大红线：
后端 RBAC、首轮 ACL、本地文件读取/SSRF/摄取 OOM、
cancel owner 与断链上游取消、Prompt Injection/日志脱敏、
SSE 幂等、MQ 消费幂等、自动 migration。

回答方式：
结论 -> 代码依据 -> 取舍 -> 当前限制 -> 下一步验证。
```

---

## 24. 关联文档

- [项目边界与完成标准](./rag/PROJECT_BOUNDARY.md)
- [Spring AI 迁移审计](./rag/SPRING_AI_MIGRATION_AUDIT.md)
- [Spring AI 迁移前基线](./rag/MIGRATION_BASELINE.md)
- [Agentic Retrieval 总规划](./rag/AGENTIC_RETRIEVAL_PLAN.md)
- [AR0 基线](./rag/AGENTIC_RETRIEVAL_BASELINE.md)
- [AR1 记录](./rag/AGENTIC_RETRIEVAL_AR1.md)
- [AR2 记录](./rag/AGENTIC_RETRIEVAL_AR2.md)
- [AR3 记录](./rag/AGENTIC_RETRIEVAL_AR3.md)
- [AR4 记录](./rag/AGENTIC_RETRIEVAL_AR4.md)
- [学园偶像大师演示知识库](./rag/GAKUMAS_KNOWLEDGE_BASE.md)

阶段文档记录的是当时配置和验收结果。遇到默认超时、启用状态或运行拓扑冲突时，以当前代码、`application.yaml`、`deploy/application-docker.yaml` 和本手册的快照说明为准。
