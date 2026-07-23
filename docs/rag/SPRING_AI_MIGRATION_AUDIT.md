# ragent Spring AI 迁移审计

## 1. 结论

当前 `ragent` 没有使用 Spring AI。它使用 Spring Boot 3.5.7、自研模型访问层、自研 RAG 流水线和官方 MCP Java SDK 1.1.2。

迁移不能理解为“把 RAG 改成 Spring AI RAG”。正确做法是：

```text
保留 ragent 的业务接口和 RAG 编排
  -> 在接口后增加 Spring AI 模型适配器
  -> 双实现回归
  -> 分阶段删除自研协议代码
```

## 2. 版本选择

当前项目基于 Spring Boot 3.5.7，因此选择 Spring AI 1.1.x 稳定线，不直接升级到 Spring AI 2.x。

- Spring AI 官方兼容矩阵：1.1.x 对应 Spring Boot 3.5.x；2.x 对应 Spring Boot 4.x。
- 审计时稳定文档列出的 1.1.x 版本为 1.1.8。
- Spring AI 2.x 同时升级到 MCP Java SDK 2.x，并包含多项破坏性变更，不适合作为本次渐进迁移起点。

实际引入时通过 `spring-ai-bom` 固定 1.1.x 版本，禁止各模块单独声明 Spring AI 组件版本。

官方参考：

- https://github.com/spring-projects/spring-ai
- https://docs.spring.io/spring-ai/reference/getting-started.html
- https://docs.spring.io/spring-ai/reference/upgrade-notes.html

## 3. 当前技术栈事实

### 3.1 模型调用

当前链路：

```text
业务代码
  -> LLMService
  -> RoutingLLMService
  -> ModelSelector / ModelRoutingExecutor / ModelHealthStore
  -> 自研 ChatClient
  -> AbstractOpenAIStyleChatClient
  -> OkHttp + 自研 SSE 解析
```

已适配 BaiLian、SiliconFlow、AIHubMix 和 Ollama。

### 3.2 Embedding

当前链路：

```text
摄取 / 查询
  -> EmbeddingService
  -> RoutingEmbeddingService
  -> 自研 EmbeddingClient
  -> AbstractOpenAIStyleEmbeddingClient
  -> OkHttp
```

路由层支持指定模型、候选模型和失败降级。

### 3.3 RAG 业务链

当前已经自研：

- 摄取 Pipeline 与节点状态；
- Tika 解析和结构感知切片；
- 文档、知识库、Chunk 和定时同步；
- pgvector / Milvus 双向量存储；
- Query Rewrite、意图识别和术语映射；
- 多通道检索、去重和 Rerank；
- Prompt 计划与上下文格式化；
- 会话、摘要、反馈和 Trace；
- SSE、限流、取消和超时。

这些能力不能因为引入 Spring AI 而被框架默认组件整体替换。

## 4. 保留、替换、整合矩阵

| 当前组件 | 决策 | 目标方式 | 原因 |
| --- | --- | --- | --- |
| `LLMService` | 迁移期保留 | 作为业务兼容端口，新增 Spring AI 实现 | 避免一次修改所有调用方 |
| 自研 `ChatClient` | 最终替换 | Spring AI `ChatModel` / `ChatClient` | 与 Spring AI 同名且主要负责协议适配 |
| `AbstractOpenAIStyleChatClient` | 最终删除 | Spring AI OpenAI/Ollama 模型实现 | 不再维护请求 JSON、HTTP 和 SSE 协议 |
| 各 Provider ChatClient | 最终删除或极薄配置化 | OpenAI 兼容 base URL 或官方 Provider Starter | 减少重复厂商适配 |
| `RoutingLLMService` | 第一阶段保留 | 路由到多个 Spring AI `ChatModel` Bean | Spring AI 不等于业务级多模型故障转移 |
| `ModelSelector` | 保留 | 继续负责候选模型选择 | 属于项目模型治理策略 |
| `ModelRoutingExecutor` | 保留后复评 | 包装 Spring AI 模型调用 | 当前已有 fallback 和健康熔断语义 |
| `ModelHealthStore` | 保留后复评 | 接入 Spring AI 异常分类和观测 | 业务需要降级状态，不应直接丢失 |
| `StreamCallback` | 迁移期保留 | 用 Flux 适配现有 SSE 回调 | 避免同时重写前端流协议 |
| `StreamCancellationHandle` | 迁移期保留 | 包装 Reactor `Disposable` | 保留当前取消契约 |
| 自研 SSE Parser | 流式迁移后删除 | Spring AI Streaming ChatModel | 属于模型协议细节 |
| `EmbeddingService` | 迁移期保留 | 新增 Spring AI `EmbeddingModel` 适配器 | 摄取和查询调用方保持稳定 |
| 自研 EmbeddingClient | 最终替换 | Spring AI EmbeddingModel | 不再维护 OpenAI 兼容请求协议 |
| `RoutingEmbeddingService` | 保留 | 路由到多个 EmbeddingModel | 模型降级和指定模型是现有业务能力 |
| `RerankService` | 保留 | 继续使用现有 BaiLian/Noop 路由 | Spring AI 基础模型抽象不能替代现有精排业务 |
| `TokenCounterService` | 暂时保留 | 后续与模型 Usage 统一 | 启发式预算和实际账单 Token 是两个概念 |
| `VectorStoreService/Admin` | 保留 | 继续使用 pgvector / Milvus 自研适配 | 包含 collection、维度和业务元数据语义 |
| `RetrievalEngine` | 保留 | 作为自研 RAG Orchestrator | 项目核心差异化能力 |
| SearchChannel / PostProcessor | 保留 | 继续扩展 ACL、关键词和分数融合 | 框架默认 Advisor 无法表达现有策略 |
| `PromptTemplateLoader` / PromptPlan | 保留并适配 | 转换为 Spring AI Message/Prompt | 模板和场景属于业务资产 |
| Conversation Memory | 保留 | 必要时通过 Advisor 读取现有 MemoryService | 已有摘要、持久化和业务表结构 |
| Rag Trace | 整合 | 保留业务节点，接入 Spring AI Observation | 框架 Trace 记录模型层，现有 Trace 记录业务层 |
| 官方 MCP SDK 直连 | 后期迁移 | Spring AI MCP Client Starter | 不作为首个迁移点，避免同时改变模型和工具协议 |
| 自研 Agent 包 | 停止演进 | Spring AI Tool Calling 或从 ragent 移除 | 通用 Agent Runtime 已归属 KoawaAgent |
| Ingestion 全链路 | 保留 | Embedding 节点内部调用 Spring AI 适配器 | 文档工程是 ragent 的核心资产 |

## 5. 命名冲突

项目当前存在：

```java
com.koawa.agent.infra.chat.ChatClient
```

Spring AI 同时存在：

```java
org.springframework.ai.chat.client.ChatClient
```

第一阶段不要在业务代码中同时裸用两个 `ChatClient`。迁移适配器必须使用完整包名或将当前 Provider 级接口重命名为 `ProviderChatClient`。重命名应在 Spring AI 适配器可工作后进行，避免把机械重构和行为迁移混在一个提交中。

## 6. Provider 适配判断

| Provider | 初步接入方式 | 必须验证 |
| --- | --- | --- |
| SiliconFlow | Spring AI OpenAI 兼容模型，自定义 base URL | thinking 参数、流式 reasoning、Embedding dimensions |
| AIHubMix | Spring AI OpenAI 兼容模型，自定义 base URL | 模型名称、Usage、错误响应格式 |
| Ollama | Spring AI Ollama Starter | thinking、超时、Embedding 批处理 |
| BaiLian | 优先验证 OpenAI 兼容入口；不满足时保留薄适配器 | 流式协议、Rerank 不属于 ChatModel |

不能仅凭“OpenAI 兼容”就删除旧实现。每个 Provider 至少需要同步、流式、取消、错误映射和 Usage 五类契约测试。

## 7. 分阶段迁移顺序

### Phase 0：基线

- 全量编译和单元测试；
- 固定至少一组模型适配契约测试；
- 建立不依赖线上模型的 RAG 检索评测集；
- 记录当前依赖树和关键调用链。

### Phase 1：同步 Chat

- 引入 Spring AI 1.1.x BOM 和最小模型依赖；
- 新增 `SpringAiLlmService` 或等价适配器；
- 将现有 `ChatRequest` 映射为 Spring AI Prompt/Options；
- 通过配置 `legacy|spring-ai` 切换；
- 只验证同步调用，不修改 SSE。

### Phase 2：Embedding

- 新增 `SpringAiEmbeddingService` 适配器；
- 验证单条、批量、顺序、维度和异常；
- 使用相同文本对比新旧向量维度和检索结果；
- 不替换 VectorStore 和 Indexer。

### Phase 3：流式 Chat

- 将 Spring AI Flux 适配到现有 `StreamCallback`；
- 保留取消、首包超时、reasoning 和最终完成语义；
- 验证客户端断开后上游模型请求被取消；
- 稳定后删除自研 SSE Parser。

### Phase 4：Observation 与模型治理

- 将 Spring AI ChatModel、EmbeddingModel Observation 接入现有 Trace；
- 对齐模型 ID、Provider、Token、延迟和错误分类；
- 复评自研健康检查与 fallback，保留业务需要的部分。

### Phase 5：Tool/MCP 与旧代码清理

- 评估 Spring AI MCP Client Starter；
- 删除 ragent 中不属于知识业务的演示 MCP 工具；
- 完成调用方迁移后删除旧 Provider Client；
- 最后处理自研 Agent 兼容包。

## 8. 配置与回滚

迁移期建议增加独立开关：

```yaml
ai:
  adapter:
    chat: legacy
    embedding: legacy
```

切换粒度应按能力区分，不能只设置一个全局 `spring-ai.enabled`。否则 Chat 稳定而 Embedding 异常时无法单独回滚。

在旧实现删除前，回滚方式是修改配置并重启；数据库、向量 schema、现有 API 和前端 SSE 协议保持不变。

## 9. 主要风险与验证

| 风险 | 可能后果 | 验证方式 |
| --- | --- | --- |
| 流式增量语义不同 | 重复输出、漏输出或无法结束 SSE | 固定事件序列测试和断开取消测试 |
| reasoning 字段丢失 | 深度思考 UI 退化 | Provider 契约测试 |
| Embedding 维度或顺序变化 | 索引不可写或召回错误 | 批量顺序、维度和真实 pgvector 集成测试 |
| Spring 异常类型不同 | fallback 不触发或错误归类错误 | HTTP 4xx、429、5xx、超时和空响应测试 |
| 双 ChatClient 命名冲突 | 误注入或代码难读 | 适配器隔离和 Bean Qualifier 测试 |
| 自动配置创建多个模型 Bean | ChatClient.Builder 注入歧义 | 启动上下文测试和显式 Qualifier |
| Trace 重复记录 | 指标翻倍或父子关系错误 | 单次请求 Trace 树断言 |
| Prompt 迁移改变消息顺序 | 回答质量下降 | Prompt 快照与固定问答回归 |

## 10. 首个实现切片

完成 Phase 0 前不修改生产模型调用。首个代码切片应当是：

```text
Spring AI 依赖验证测试
  -> 单独构造一个 ChatModel
  -> 现有 ChatRequest 到 Spring Prompt 的纯映射器
  -> 映射器单元测试
  -> 不注册为 @Primary
  -> 不改变现有 RAGChatService
```

这样可以验证依赖和类型设计，同时保持现有业务链路零行为变化。
