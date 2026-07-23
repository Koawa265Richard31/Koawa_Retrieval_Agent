# ragent 企业知识库助手：项目边界与完成标准

## 1. 项目定位

`ragent` 的目标是一个可部署、可评测、可追溯的企业知识库助手，而不是通用 Agent Runtime。

它解决的核心问题是：

```text
企业文档分散且格式不一
  -> 可配置摄取、解析和切片
  -> 增量向量化与索引
  -> 多通道召回、去重和 Rerank
  -> 带来源引用的多轮问答
  -> 权限、反馈、评测和链路追踪
```

目标用户包括企业内部员工、知识库管理员和负责效果调优的研发人员。

## 2. 与 KoawaAgent 的边界

两个项目在简历和代码职责上必须分开：

| 项目 | 核心目标 | 主要技术取舍 |
| --- | --- | --- |
| KoawaAgent | 展示可控 Agent Runtime 的底层设计 | 自研 Plan-Act Loop、状态、恢复、策略、取消和 MCP 编排 |
| ragent | 交付企业知识摄取、检索和问答业务 | 通用 AI 接口采用 Spring AI，自研 RAG 流水线和业务治理 |

`ragent` 不再继续扩展自研通用 Agent Runtime。现有 `bootstrap/.../agent` 代码只作为迁移期兼容实现，在 Spring AI 问答链路稳定并完成回归后再决定删除，不在迁移初期直接移除。

## 3. Spring AI 负责的通用能力

计划由 Spring AI 接管：

- 模型提供商的 `ChatModel` / `EmbeddingModel` 抽象；
- 同步和流式模型调用；
- ChatClient 请求组装及基础 Advisor 扩展点；
- 标准 Tool Calling 和 MCP Client 接入；
- 模型响应、用量和基础观测接口；
- 与模型协议强相关、但不包含业务决策的适配代码。

采用 Spring AI 的目的不是减少代码行数，而是停止维护 OpenAI 兼容协议、SSE 解析和不同模型提供商的重复适配。

## 4. 必须保留的自研 RAG 能力

以下能力是 ragent 的核心资产，不交给框架默认实现替换：

### 4.1 文档摄取

- `IngestionEngine` 与可配置 Pipeline；
- Fetcher、Parser、Chunker、Enhancer、Enricher、Indexer 节点；
- 本地文件、HTTP、对象存储、飞书等来源适配；
- 文档任务状态、节点日志、失败重试和幂等处理；
- Tika 文档解析与结构感知切片；
- 内容哈希、增量更新和旧索引替换。

### 4.2 检索与排序

- `RetrievalEngine` / `MultiChannelRetrievalEngine`；
- 意图定向召回和全局向量召回；
- pgvector / Milvus 存储适配；
- 多通道结果去重、分数处理和 Rerank；
- Query Rewrite、子问题拆分和术语映射；
- 检索上下文及引用来源格式化。

### 4.3 企业业务治理

- 知识库、文档、Chunk 和定时同步管理；
- 用户、知识库和文档级访问控制；
- 会话、消息、摘要和用户反馈；
- 运行 Trace、节点耗时和失败定位；
- 离线评测集、检索指标和答案质量回归；
- 限流、超时、取消和部署配置。

## 5. 明确不做的内容

当前阶段不在 ragent 中继续实现：

- 自研通用 Agent Framework；
- 多 Agent、Supervisor、A2A 和 Skills 市场；
- 通用工作流可视化设计器；
- 任意 Shell、浏览器或桌面操作沙箱；
- 与企业知识问答无关的天气、票务、销售等演示工具；
- 为追求框架一致性而重写稳定的摄取和检索流水线。

如果知识问答中需要工具调用，优先使用 Spring AI Tool Calling 或 MCP，并限制为与知识业务直接相关的只读或受控操作。

## 6. 目标架构

```text
Controller / SSE
        |
Enterprise Knowledge Application Service
        |
        +-- Spring AI ChatClient / ChatModel
        |       +-- 模型调用
        |       +-- 流式响应
        |       +-- Tool / MCP
        |
        +-- Custom RAG Orchestrator
                +-- Query Rewrite
                +-- Intent Resolution
                +-- Multi-channel Retrieval
                +-- Deduplication / Rerank
                +-- ACL Filtering
                +-- Citation Formatting

Document Source
        |
Custom Ingestion Pipeline
        +-- Fetch -> Parse -> Chunk -> Enrich -> Embed -> Index
                                             |
                                  Spring AI EmbeddingModel
                                             |
                                   pgvector / Milvus
```

核心原则：Spring AI 位于模型协议边界，自研 RAG 位于业务编排边界。

## 7. 迁移原则

采用适配器加逐步替换，不做一次性重写：

1. 先建立当前编译、单测和 RAG 评测基线。
2. 在现有 `LLMService` / `EmbeddingService` 接口后增加 Spring AI 实现。
3. 保持上层 RAG 调用方不变，通过配置切换旧实现和 Spring AI 实现。
4. 先迁移同步 Chat，再迁移 Embedding，最后迁移流式 Chat。
5. 每迁移一层都运行相同评测集，对比质量、延迟、Token 和失败类型。
6. 新实现稳定后才删除旧 OkHttp Provider 和自研 SSE 解析代码。

任何阶段都应能通过配置切回旧实现，直到迁移完成。

## 8. 完成标准

### 8.1 功能闭环

- 管理员可以创建知识库并导入受支持的文档。
- 文档能够经过解析、切片、Embedding 和索引，失败时能看到节点原因。
- 重复导入相同内容不会生成重复有效索引。
- 用户提问后能得到有来源引用的答案。
- 多轮追问能够使用受控的会话历史或摘要。
- 知识库内容更新后可以增量刷新，旧 Chunk 不再被召回。
- 无权限用户不能检索或引用受限文档。

### 8.2 质量基线

建立不少于 50 条、包含答案依据和目标文档的固定评测集，并至少记录：

- Recall@5；
- MRR；
- Rerank 前后 Recall@5 / MRR 变化；
- 引用命中率和引用正确率；
- 无答案问题的拒答正确率；
- 平均及 P95 首包延迟、总延迟；
- 每次回答的模型调用次数和 Token 消耗；
- 失败率及失败类型分布。

迁移 Spring AI 后，检索质量不得低于迁移前基线。具体目标值在首轮真实评测完成后确定，不能先用主观数字代替基线。

### 8.3 工程质量

- 核心摄取、检索、排序、权限过滤和模型适配具备单元测试；
- 至少有一条使用真实 PostgreSQL/pgvector 的集成测试；
- 配置中不提交密钥，启动前能校验必要环境变量；
- Docker Compose 能在 Linux 2 核 8 GB 实例上启动核心链路；
- Health Check 能区分应用存活、数据库可用和模型配置状态；
- Trace 可以定位一次问答经过的改写、召回、Rerank 和生成节点；
- 发布前保留可执行的回滚路径。

## 9. 简历表达边界

项目完成后可以概括为：

> 基于 Spring AI 与自研 RAG 流水线构建企业知识库助手，支持多源文档摄取、结构化切片、pgvector/Milvus 双存储、多通道召回、Rerank、会话记忆、来源引用、权限过滤和离线评测；通过适配器完成自研模型客户端向 Spring AI 的渐进迁移，并利用链路 Trace 对检索质量、延迟和失败进行观测。

在相关能力真正完成和测量之前，不在简历中声明具体准确率、并发量或生产规模。
