# ragent Agentic Retrieval 可执行规划

## 1. 文档目的

本文定义 `ragent` 在现有企业知识库问答架构上引入 Agentic Retrieval 的实施方案。

目标不是实现通用 Agent，也不是照搬 AWS `AgenticRetrieveStream`，而是把当前“一次改写、一次检索、直接生成”的流程升级为：

```text
复杂问题识别
  -> 检索任务规划
  -> 复用现有 RetrievalEngine 执行检索
  -> 按子问题评估证据充分性
  -> 对证据缺口进行有限次补检索
  -> 合并、去重、Rerank 和引用整理
  -> 生成答案或明确拒答
```

该能力属于自研 RAG 编排，不属于通用 Agent Runtime。

## 2. 结论与实施原则

### 2.1 架构结论

Agentic Retrieval 应落在 `Custom RAG Orchestrator` 内部，位于 `StreamChatPipeline` 和现有 `RetrievalEngine` 之间。

目标调用关系：

```text
StreamChatPipeline
  -> AgenticRetrievalOrchestrator
       -> RetrievalTaskPlanner
       -> RetrievalEngine
       -> EvidenceEvaluator
       -> EvidenceAccumulator
       -> RetrievalTraceSink
  -> Answer Generation
```

现有 `RetrievalEngine` 继续负责：

- 意图定向召回；
- 全局向量召回；
- 多知识库并行检索；
- 结果去重和后处理；
- Rerank；
- 上下文格式化。

新增编排层负责：

- 判断是否需要 Agentic Retrieval；
- 管理检索轮次和预算；
- 维护每个子问题的证据状态；
- 发现证据缺口并生成补充查询；
- 输出统一的最终检索结果和轨迹。

### 2.2 不采用的方案

不直接在 `AgentLoopRunner` 中实现检索迭代，原因如下：

1. `ragent` 的项目边界是不再扩展自研通用 Agent Runtime。
2. 外层 Agent 步数和内层检索轮次是两种不同预算，混用后难以控制成本。
3. 检索需要 Chunk、引用、知识库和子问题级状态，通用 `Observation` 粒度不足。
4. 普通 RAG 请求也需要使用该能力，不能依赖 Agent 路由才生效。

兼容期的 `RetrieveKbActionHandler` 可以调用新编排器，但不是目标架构的主入口。

### 2.3 实施原则

- 默认关闭，通过配置灰度开启。
- 简单问题继续走现有单轮检索。
- 第一版最多允许一次补检索，即总计最多两轮。
- 不在第一版引入多 Agent、图工作流或新的向量数据库。
- 不修改现有 `RetrievalEngine` 的主要检索语义。
- 每个阶段均可独立回滚。
- 没有固定评测集之前，不宣称质量提升。

## 3. 当前架构基线

### 3.1 已有能力

| 能力 | 当前实现 | 处理方式 |
| --- | --- | --- |
| 问题改写和子问题拆分 | `MultiQuestionRewriteService` | 复用 |
| 多子问题检索 | `RetrievalEngine` | 复用 |
| 多通道并行召回 | `MultiChannelRetrievalEngine` | 复用 |
| 知识库定向检索 | `IntentDirectedSearchChannel` | 复用 |
| 全局向量召回 | `VectorGlobalSearchChannel` | 复用 |
| 去重和 Rerank | `SearchResultPostProcessor` | 复用 |
| 检索结果结构 | `RetrievalContext` | 适配并逐步扩展 |
| 空检索处理 | `StreamChatPipeline.handleEmptyRetrieval` | 升级为证据决策 |
| 运行 Trace | RAG Trace 相关实体和服务 | 扩展节点类型 |
| RAG 离线评测入口 | `EvalController` | 扩展多轮指标 |

### 3.2 当前主要缺口

当前链路缺少：

- 简单查询与复杂查询的稳定路由；
- 显式的检索计划；
- 子问题级证据账本；
- 证据充分性判断；
- 缺口驱动的补充查询；
- 检索轮次、模型次数和总耗时预算；
- 多轮检索轨迹；
- 多轮检索独立评测指标；
- 答案与证据之间稳定的引用映射。

## 4. 目标领域模型

建议新增包：

```text
com.koawa.agent.rag.core.agentic
```

### 4.1 AgenticRetrievalRequest

```java
public record AgenticRetrievalRequest(
        String question,
        String conversationId,
        String userId,
        List<SubQuestionIntent> initialSubIntents,
        int topK,
        RetrievalBudget budget
) {
}
```

约束：

- `question` 必填；
- `initialSubIntents` 允许为空，由 Planner 创建；
- `userId` 必须向下传递，用于 ACL；
- Request 不包含可变执行状态。

### 4.2 RetrievalBudget

```java
public record RetrievalBudget(
        int maxIterations,
        int maxSubQueries,
        int maxRetrievedChunks,
        Duration timeout
) {
}
```

第一版默认值：

```text
maxIterations = 2
maxSubQueries = 6
maxRetrievedChunks = 40
timeout = 8s
```

说明：

- `maxIterations=2` 表示首次检索加最多一次补检索。
- 预算耗尽时返回当前最佳证据，不抛出无限重试类异常。
- 超时必须复用现有取消和线程池治理方式，不能创建无界线程。

### 4.3 RetrievalPlan

```java
public record RetrievalPlan(
        List<RetrievalTask> tasks,
        String rationale
) {
}
```

```java
public record RetrievalTask(
        String taskId,
        String question,
        List<String> knowledgeBaseIds,
        Set<String> requiredFacts,
        boolean dependsOnPreviousEvidence
) {
}
```

第一版不实现任意 DAG：

- 无依赖任务允许并行；
- `dependsOnPreviousEvidence=true` 的任务进入下一轮；
- 不引入图数据库或工作流引擎。

### 4.4 EvidenceItem

```java
public record EvidenceItem(
        String taskId,
        String chunkId,
        String documentId,
        String knowledgeBaseId,
        String content,
        double score,
        String sourceTitle,
        String sourceUri,
        int iteration
) {
}
```

要求：

- `chunkId + documentId` 能稳定定位原始证据；
- 保留原始分数和所在轮次；
- 不把格式化后的大段 Prompt 当成唯一证据存储；
- ACL 过滤必须发生在 Evidence 进入账本之前。

### 4.5 EvidenceLedger

```java
public final class EvidenceLedger {

    private final Map<String, TaskEvidenceState> taskStates;
    private final List<EvidenceItem> evidence;
    private final List<RetrievalIteration> iterations;
}
```

每个子任务至少记录：

```text
PENDING
SUPPORTED
PARTIALLY_SUPPORTED
UNSUPPORTED
CONFLICTED
```

EvidenceLedger 是 Agentic Retrieval 的核心状态，不复用通用 AgentState。

### 4.6 EvidenceEvaluation

```java
public record EvidenceEvaluation(
        boolean sufficient,
        List<TaskAssessment> assessments,
        List<RetrievalGap> gaps,
        double confidence,
        String explanation
) {
}
```

第一版的 `confidence` 仅用于观测和评测，不作为绝对事实概率。

### 4.7 AgenticRetrievalResult

```java
public record AgenticRetrievalResult(
        RetrievalContext retrievalContext,
        EvidenceLedger evidenceLedger,
        RetrievalStopReason stopReason,
        int iterationCount,
        boolean sufficient
) {
}
```

停止原因：

```text
SUFFICIENT
BUDGET_EXHAUSTED
NO_NEW_EVIDENCE
TIMEOUT
PLANNING_FAILED
RETRIEVAL_FAILED
EVALUATION_FAILED
```

结果必须尽量返回已获得的有效证据。只有无法构造任何可信结果时才返回失败状态。

## 5. 核心接口设计

### 5.1 AgenticRetrievalOrchestrator

```java
public interface AgenticRetrievalOrchestrator {

    AgenticRetrievalResult retrieve(AgenticRetrievalRequest request);
}
```

职责：

1. 校验和规范化预算；
2. 选择单轮或 Agentic 模式；
3. 调用 Planner；
4. 执行 RetrievalEngine；
5. 累积和去重 Evidence；
6. 调用 EvidenceEvaluator；
7. 根据缺口决定继续或停止；
8. 构造最终 `RetrievalContext`；
9. 发布检索轨迹。

不负责：

- 最终答案生成；
- 对话历史压缩；
- 通用 Tool Calling；
- 修改文档或知识库；
- 用户澄清交互。

### 5.2 RetrievalComplexityDecider

```java
public interface RetrievalComplexityDecider {

    RetrievalMode decide(
            String question,
            List<SubQuestionIntent> subIntents
    );
}
```

模式：

```text
SINGLE_PASS
AGENTIC
```

第一阶段采用确定性规则：

- 只有一个明确实体和一个事实目标：`SINGLE_PASS`；
- 子问题数量大于 1：`AGENTIC`；
- 包含比较、跨时间、条件判断、归因或多知识库意图：`AGENTIC`；
- 无法判断时保持 `SINGLE_PASS`。

规则稳定并有评测集后，再考虑使用小模型分类。

### 5.3 RetrievalTaskPlanner

```java
public interface RetrievalTaskPlanner {

    RetrievalPlan initialPlan(PlanningContext context);

    RetrievalPlan followUpPlan(
            PlanningContext context,
            EvidenceEvaluation evaluation
    );
}
```

实现建议：

- `RuleBasedRetrievalTaskPlanner`：负责已有子问题到 Task 的无模型转换；
- `LlmRetrievalTaskPlanner`：只处理复杂问题和补检索；
- LLM 输出必须经过 JSON Schema 校验；
- Planner 失败时允许回退到现有子问题列表。

### 5.4 EvidenceEvaluator

```java
public interface EvidenceEvaluator {

    EvidenceEvaluation evaluate(
            RetrievalPlan plan,
            EvidenceLedger ledger
    );
}
```

建议分成两层：

```text
DeterministicEvidenceChecks
  -> 空结果
  -> Chunk 数量
  -> 来源数量
  -> 重复证据
  -> 每个任务是否命中

LlmEvidenceEvaluator
  -> 证据是否真正回答任务
  -> 是否存在缺失条件
  -> 是否存在冲突
  -> 生成下一轮 RetrievalGap
```

确定性检查先运行。明显为空或明显完整的情况不调用 LLM。

### 5.5 EvidenceAccumulator

```java
public interface EvidenceAccumulator {

    EvidenceLedger merge(
            EvidenceLedger current,
            RetrievalIterationResult iteration
    );
}
```

第一版去重键：

```text
documentId + chunkId
```

后续可增加内容哈希去重，但不能仅按文本截断值去重。

## 6. 执行状态机

```text
START
  |
  v
DECIDE_MODE
  | SINGLE_PASS
  +--------------------> EXISTING_RETRIEVAL -> COMPLETE
  |
  | AGENTIC
  v
PLAN
  |
  v
RETRIEVE
  |
  v
ACCUMULATE
  |
  v
EVALUATE
  | sufficient
  +--------------------> FINALIZE -> COMPLETE
  |
  | insufficient
  v
CHECK_BUDGET
  | exhausted / timeout / no new evidence
  +--------------------> FINALIZE_PARTIAL -> COMPLETE
  |
  v
REPLAN
  |
  +--------------------> RETRIEVE
```

硬性终止条件：

- 达到 `maxIterations`；
- 达到 `maxSubQueries`；
- 达到 `maxRetrievedChunks`；
- 超过总超时；
- 新一轮没有新增 Evidence；
- 连续两轮产生相同规范化查询；
- 调用方取消请求。

## 7. 与现有代码的集成点

### 7.1 StreamChatPipeline

当前：

```text
rewrite -> retrieve -> handleEmptyRetrieval -> generate
```

目标：

```text
rewrite
  -> agenticRetrievalOrchestrator.retrieve
  -> handleRetrievalDecision
  -> generate
```

新增 `handleRetrievalDecision`，统一处理：

- 证据充分：正常生成；
- 部分充分：使用明确的保守 Prompt 生成；
- 无有效证据：拒答；
- 超时但存在证据：使用当前最佳证据；
- 规划或评估失败：按配置回退现有单轮检索。

### 7.2 RetrievalEngine

第一阶段不修改公开方法：

```java
RetrievalContext retrieve(
        List<SubQuestionIntent> subIntents,
        int topK
);
```

新增适配器把 `RetrievalTask` 转换成 `SubQuestionIntent`，避免第一阶段侵入检索内核。

第二阶段根据真实需要再考虑增加：

```java
RetrievalContext retrieve(RetrievalRequest request);
```

不能为了形式统一提前重写现有调用方。

### 7.3 RetrieveKbActionHandler

迁移期行为：

- 默认继续使用现有 `RetrievalEngine`；
- 增加配置允许调用 `AgenticRetrievalOrchestrator`；
- Observation 中返回摘要、充分性、轮次和来源数量；
- 不把完整 EvidenceLedger 序列化进 Planner Prompt；
- Trace 中保留完整账本引用。

目标状态：

- RAG 主链路不依赖该 Handler；
- KoawaAgent 如需 Agentic Retrieval，应通过独立 MCP 或 API 调用。

### 7.4 Prompt

需要新增三类模板：

```text
rag/agentic/retrieval-plan
rag/agentic/evidence-evaluation
rag/agentic/follow-up-plan
```

模板约束：

- 只输出结构化 JSON；
- 明确禁止根据常识补齐缺失证据；
- 每个结论必须引用 `evidenceId`；
- 缺口必须转换为可检索查询；
- 不把 Planner rationale 展示给最终用户。

最终回答 Prompt 增加：

- `evidenceSufficient`；
- `unsupportedTasks`；
- `conflicts`；
- `stopReason`；
- 来源引用列表。

## 8. Trace 与可观测性

### 8.1 新增 Trace 节点

建议扩展现有 RAG Trace，而不是另建通用 Agent Event：

```text
RETRIEVAL_MODE_DECISION
RETRIEVAL_PLAN
RETRIEVAL_ITERATION
EVIDENCE_ACCUMULATION
EVIDENCE_EVALUATION
RETRIEVAL_REPLAN
RETRIEVAL_FINALIZE
```

### 8.2 每轮必须记录

- traceId / conversationId；
- iteration；
- 规范化子查询；
- 目标知识库；
- 候选 Chunk 数量；
- 去重后 Chunk 数量；
- Rerank 后 Chunk 数量；
- 新增 Evidence 数量；
- 每个任务的证据状态；
- 模型调用次数；
- Token 用量；
- 节点耗时；
- 终止原因；
- 失败类型。

### 8.3 指标

新增指标：

```text
rag_agentic_requests_total
rag_agentic_iterations
rag_agentic_sufficient_total
rag_agentic_budget_exhausted_total
rag_agentic_no_new_evidence_total
rag_agentic_retrieval_latency
rag_agentic_planner_latency
rag_agentic_evaluator_latency
rag_agentic_model_calls
rag_agentic_retrieved_chunks
```

日志中禁止打印：

- 完整敏感文档内容；
- 用户 Token；
- 模型 API Key；
- 未脱敏的个人信息。

## 9. 配置与灰度

建议配置：

```yaml
rag:
  agentic-retrieval:
    enabled: false
    mode: shadow
    max-iterations: 2
    max-sub-queries: 6
    max-retrieved-chunks: 40
    timeout: 8s
    planner-model: ""
    evaluator-model: ""
    fallback-to-single-pass: true
    complex-query-routing-enabled: true
    full-document-expansion-enabled: false
```

`mode`：

```text
off
shadow
active
```

- `off`：只走当前链路；
- `shadow`：后台执行 Agentic Retrieval 并记录指标，但回答仍使用当前结果；
- `active`：回答使用 Agentic Retrieval 结果。

灰度键建议使用稳定哈希：

```text
conversationId 优先
userId 兜底
```

不能对同一会话逐请求随机切换检索模式。

## 10. 分阶段实施

### Phase AR0：评测与行为基线

状态：已完成（2026-07-24）。执行记录见
[`AGENTIC_RETRIEVAL_BASELINE.md`](./AGENTIC_RETRIEVAL_BASELINE.md)，机器可读摘要见
`resources/eval/agentic-retrieval/v1/baseline-summary.json`。

#### 目标

建立可判断 Agentic Retrieval 是否有效的基线，不修改生产行为。

#### 工作项

1. 扩展固定评测集，新增至少 20 条复杂问题：
   - 多实体比较；
   - 多知识库组合；
   - 时间版本判断；
   - 条件和审批规则；
   - 需要前一结果才能继续的多跳问题；
   - 知识库中不存在答案的问题。
2. 每条数据记录：
   - 期望子问题；
   - 目标知识库；
   - 目标文档或 Chunk；
   - 必需事实；
   - 是否应拒答。
3. 记录当前单轮结果：
   - Recall@5；
   - MRR；
   - 引用命中率；
   - 拒答正确率；
   - 平均/P95 延迟；
   - 模型调用次数和 Token。

#### 交付物

- Agentic Retrieval 评测数据；
- 基线执行脚本；
- 基线报告；
- 失败样例分类。

#### 验收

- 同一版本重复执行结果可比较；
- 复杂问题和简单问题分组统计；
- 不依赖手工阅读所有回答才能得到检索指标。

### Phase AR1：领域模型与 Shadow Evidence Evaluation

状态：已完成（2026-07-24）。实现与验收记录见
[`AGENTIC_RETRIEVAL_AR1.md`](./AGENTIC_RETRIEVAL_AR1.md)。

#### 目标

建立 EvidenceLedger 和证据评估能力，但不影响线上回答。

#### 工作项

1. 新增：
   - `RetrievalBudget`；
   - `RetrievalTask`；
   - `EvidenceItem`；
   - `EvidenceLedger`；
   - `EvidenceEvaluation`；
   - `AgenticRetrievalResult`。
2. 实现 `RetrievalContextEvidenceAdapter`。
3. 实现确定性 Evidence Checks。
4. 实现 `LlmEvidenceEvaluator` 和输出 Parser。
5. 增加 Shadow 配置。
6. 扩展 Trace 和指标。

#### 测试

- Evidence 去重；
- 空证据；
- 一个任务有证据、一个任务无证据；
- 冲突证据；
- Evaluator 非法 JSON；
- Evaluator 超时；
- Shadow 结果不得改变当前回答。

#### 验收

- Shadow 模式下原有 API、SSE 和回答内容不变；
- 能看到每个子问题的证据状态；
- Evaluator 失败不会使问答失败；
- 全量测试通过。

### Phase AR2：一次补检索最小闭环

#### 目标

对证据不足的复杂问题执行最多一次补检索。

#### 工作项

1. 实现 `RuleBasedRetrievalTaskPlanner`。
2. 实现 `LlmRetrievalTaskPlanner.followUpPlan`。
3. 实现 `DefaultAgenticRetrievalOrchestrator`。
4. 实现预算、超时、取消和重复查询检测。
5. 实现 Evidence 合并和最终 `RetrievalContext` 构造。
6. 接入 `StreamChatPipeline`，保持默认 Shadow。

#### 测试

- 首轮充分，不补检索；
- 首轮不足，第二轮补齐；
- 第二轮没有新证据，立即停止；
- 重复查询停止；
- 达到最大 Chunk 数停止；
- 总超时返回已有证据；
- 调用取消能终止后续检索；
- 第二轮检索失败时回退第一轮结果。

#### 验收

- 最大执行轮次严格不超过 2；
- 无无限重试；
- 复杂评测集至少有可解释的检索改善样例；
- 简单问题的模型调用次数不增加；
- 原有单轮链路可一键回滚。

### Phase AR3：复杂度路由与小流量启用

#### 目标

只对真正复杂的问题启用 Agentic Retrieval。

#### 工作项

1. 实现规则版 `RetrievalComplexityDecider`。
2. 为路由加入离线混淆矩阵：
   - 简单问题误判为复杂；
   - 复杂问题漏判为简单。
3. 支持按会话稳定灰度。
4. 增加管理配置展示。
5. 对比 Shadow 和 Active 结果。

#### 初始准入门槛

以下是灰度准入条件，不是最终质量承诺：

- 简单问题 Agentic 误路由率低于 10%；
- 复杂问题检索 Recall 不低于单轮基线；
- P95 总延迟不超过单轮基线的 2.5 倍；
- 平均额外模型调用不超过 2 次；
- 失败时单轮回退成功率为 100%；
- 没有 ACL 越权召回。

#### 灰度顺序

```text
本地评测
  -> 测试环境 active
  -> 线上 shadow
  -> 内部用户 5%
  -> 20%
  -> 50%
  -> 100% 或保持分流
```

不要求最终所有请求都使用 Agentic Retrieval。

### Phase AR4：引用、冲突与完整文档扩展

#### 目标

提升证据可解释性和复杂文档上下文完整性。

#### 工作项

1. 建立答案片段到 `EvidenceItem` 的引用映射。
2. 支持冲突证据提示。
3. 实现受控 `FullDocumentExpansion`：
   - 只允许从已命中的 Chunk 扩展；
   - 设置最大文档字符数；
   - 保持 ACL；
   - 只在 Evaluator 明确需要上下文时执行。
4. 增加引用正确率评测。

#### 验收

- 每个事实性结论能映射到来源；
- 无权限文档不能通过完整文档扩展泄漏；
- 引用不存在时不伪造来源；
- 冲突证据不能被静默合并成确定结论。

## 11. 建议提交切片

为降低审查难度，每个提交只解决一个问题：

1. `test: add agentic retrieval evaluation cases`
2. `feat: add evidence ledger domain model`
3. `feat: adapt retrieval context to evidence ledger`
4. `feat: add deterministic evidence checks`
5. `feat: add llm evidence evaluator in shadow mode`
6. `feat: add agentic retrieval trace events`
7. `feat: add bounded retrieval orchestrator`
8. `feat: add follow-up retrieval planner`
9. `feat: route complex queries to agentic retrieval`
10. `feat: add citation mapping and document expansion`

每个提交均要求：

- 单元测试通过；
- 不提交密钥；
- 不改变无关模块；
- 更新对应配置说明；
- 记录新增失败类型和回滚方式。

## 12. 测试策略

### 12.1 单元测试

重点覆盖：

- 预算边界；
- 状态转换；
- Evidence 去重；
- 缺口合并；
- 重复查询检测；
- Parser 错误；
- 回退行为；
- 终止原因。

Planner 和 Evaluator 使用固定响应，不调用真实模型。

### 12.2 组件测试

使用 Fake `RetrievalEngine` 构造：

- 第一轮不足、第二轮成功；
- 每轮返回重复 Chunk；
- 某个知识库失败；
- 慢检索触发超时；
- 取消请求。

### 12.3 集成测试

至少包含：

- PostgreSQL/pgvector 真实召回；
- 两个知识库的组合问题；
- ACL 用户不能检索受限知识库；
- Trace 中轮次和 Chunk 数正确；
- SSE 最终完成语义不变。

### 12.4 回归测试

每次变更同时执行：

- 原有单轮 RAG 评测集；
- Agentic 复杂问题评测集；
- 无答案和拒答评测集；
- 前端引用展示回归；
- Docker Compose 冒烟。

## 13. 失败语义与回退

| 失败位置 | 默认行为 |
| --- | --- |
| ComplexityDecider 失败 | 使用 `SINGLE_PASS` |
| Initial Planner 失败 | 使用已有 Query Rewrite 子问题 |
| 首轮 Retrieval 失败 | 沿用现有检索失败语义 |
| EvidenceEvaluator 失败 | 停止补检索，使用首轮结果 |
| Follow-up Planner 失败 | 停止补检索，使用已有结果 |
| 第二轮 Retrieval 失败 | 使用第一轮结果 |
| 总超时 | 返回当前最佳证据 |
| 没有有效证据 | 明确拒答 |
| ACL 检查失败 | Fail Closed，不返回相关证据 |

`fallback-to-single-pass=true` 时，回退必须记录原因，不能静默掩盖 Agentic Retrieval 的持续故障。

## 14. 安全边界

- Planner 只能选择当前用户可访问的知识库。
- 不允许模型直接构造数据库过滤表达式。
- 知识库 ID 必须由服务端白名单校验。
- 文档内容按不可信输入处理，防止文档内 Prompt Injection 改写系统计划。
- EvidenceEvaluator 只能判断证据，不可调用工具或修改数据。
- Full Document Expansion 必须再次执行 ACL。
- Trace 存储 Evidence 时遵循现有数据保留和脱敏规则。
- 对补检索生成的查询设置长度、数量和字符集限制。

## 15. 性能与容量边界

当前部署目标是 Linux 2 核 8 GB，第一版必须保持保守配置：

- 同一请求最多两个检索轮次；
- 子查询并发复用现有受控线程池；
- 不为每个请求创建新线程池；
- EvidenceLedger 设置 Chunk 数量和内容长度上限；
- Evaluator 输入只包含必要证据摘要；
- 默认不加载完整文档；
- 线上 Active 前必须采集 P95 延迟和内存。

## 16. 完成定义

Agentic Retrieval 第一阶段完成必须同时满足：

### 功能

- 复杂问题可执行首次检索、证据评估和最多一次补检索；
- 简单问题保持现有单轮路径；
- 无证据时明确拒答；
- 失败时能回退已有结果；
- 答案保留来源引用。

### 质量

- 有固定复杂问题评测集；
- 能分别统计单轮和 Agentic 结果；
- 至少证明一类多部分问题的召回改善；
- 简单问题质量不下降；
- ACL 回归全部通过。

### 工程

- 有轮次、预算、超时和取消；
- 有结构化 Trace 和停止原因；
- 有 Shadow、Active 和 Off 配置；
- 有回滚路径；
- 核心状态机和回退路径具有自动化测试；
- Docker 部署资源未超过实例限制。

## 17. 推荐的首个开发任务

不要直接写完整循环。第一项实现任务是 Phase AR0：

```text
选择 20 条复杂问题
  -> 标注期望子问题和目标证据
  -> 使用当前 RetrievalEngine 跑出基线
  -> 分类当前失败原因
  -> 再确定 EvidenceEvaluator 的最小输出字段
```

原因：

- 没有失败样例，就无法定义“证据充分”；
- 没有基线，就无法判断第二轮检索是否有价值；
- 先写完整循环容易得到一个成本更高但质量无法证明的系统。

AR0 完成后，首个生产代码切片是：

```text
EvidenceLedger
  -> RetrievalContextEvidenceAdapter
  -> DeterministicEvidenceChecks
  -> 单元测试
  -> 不接入生产回答
```

这条切片不会改变线上行为，但会建立后续所有 Agentic Retrieval 能力的状态基础。
