# KoawaAgent Agent Loop 设计

## 1. 文档目标

本文描述 KoawaAgent 从固定 RAG Pipeline 演进到 Agentic RAG 的第一版设计。

设计目标不是删除现有 `StreamChatPipeline`，而是在它旁边增加一条可控的 Agent Loop，并逐步把已有检索、MCP、Memory、Trace 和 SSE 能力变成 Agent 可以调用的能力。

当前没有确定的业务场景，因此本文使用假设场景验证架构是否合理。所有动态决策都必须在后续评估中证明收益，不能因为“Agent 更先进”就替换稳定的固定流程。

## 2. 当前系统事实

当前聊天入口：

```text
RAGChatController
  -> RAGChatServiceImpl
  -> ChatQueueLimiter
  -> StreamChatTraceRunner
  -> StreamChatPipeline
```

`RAGChatServiceImpl` 负责一次聊天任务的公共外壳：

- 创建 `conversationId` 和 `taskId`；
- 创建 SSE `StreamCallback`；
- 进入限流队列；
- 创建根 trace；
- 创建 `StreamChatContext`；
- 将请求交给 `StreamChatPipeline`。

`StreamChatPipeline.execute()` 是固定业务编排：

```text
loadMemory
  -> rewriteQuery
  -> resolveIntents
  -> handleGuidance
  -> handleSystemOnly
  -> retrieve
  -> handleEmptyRetrieval
  -> streamRagResponse
```

当前系统中的 LLM 主要承担三类工作：

- 改写和拆分问题；
- 根据意图树给意图节点打分；
- 根据已经准备好的 KB/MCP 上下文生成回答。

当前控制流仍由 Java 固定代码决定，LLM 不能根据一次检索或工具结果决定是否继续执行下一步。

## 3. 为什么不直接替换原 Pipeline

固定 Pipeline 仍然适合以下请求：

- 单一知识库问答；
- 一次检索即可回答的问题；
- 高置信度意图路由；
- 对延迟和成本敏感的场景；
- 需要稳定、可预测执行顺序的业务。

Agent Loop 更适合以下请求：

- 需要先调用工具，再根据结果检索知识库；
- 首次检索为空，需要改写后重试；
- 工具结果缺少关键维度，需要再次调用；
- 用户问题缺少必要参数，需要澄清；
- 需要组合多个来源才能完成的分析任务。

因此第一阶段采用双路径：

```text
普通 RAG 模式
  -> 保留 StreamChatPipeline

Agent 模式
  -> 新增 AgentLoopRunner
  -> 复用现有能力
```

不做一次性大改的原因：

- 可以保持现有接口行为不变；
- Agent 失败时可以回退到普通 RAG；
- 可以对同一批问题比较两种模式；
- 可以控制新增代码的影响范围；
- 可以逐步证明哪些固定步骤值得动态化。

## 4. Agent Loop 的切入位置

### 4.1 推荐切入点

最合适的切入位置是：

```text
RAGChatServiceImpl 创建聊天任务公共外壳之后
StreamChatPipeline.execute(ctx) 之前
```

公共外壳继续复用：

```text
conversationId / taskId
SSE callback
ChatQueueLimiter
StreamChatTraceRunner
StreamTaskManager
UserContext
```

业务编排根据模式分流：

```text
聊天任务公共外壳
  |
  +--> RAG mode   -> StreamChatPipeline.execute(ctx)
  |
  +--> AGENT mode -> AgentLoopRunner.run(state)
```

### 4.2 三种接入方案

方案 A：新增独立 Agent endpoint 和 service。

```text
/rag/v3/chat   -> 原 RAGChatServiceImpl
/agent/chat    -> AgentChatService
```

优点：

- 对原流程影响最小；
- 最容易独立测试；
- Agent 未完成时不会破坏现有接口。

缺点：

- SSE、限流、trace 初始化可能出现少量重复。

方案 B：在 `RAGChatServiceImpl` 中增加 mode 分支。

优点：

- 最大化复用任务公共外壳；
- 前端只需一个接口。

缺点：

- `RAGChatServiceImpl` 同时承担两种编排选择；
- Agent 未稳定时容易扩大原接口风险。

方案 C：抽取统一 `ChatExecutionStrategy`。

```text
ChatExecutionStrategy
  +-- RagPipelineStrategy
  +-- AgentLoopStrategy
```

优点：

- 长期结构最清晰；
- 公共任务外壳只实现一次。

缺点：

- 当前阶段会提前引入更多抽象和重构。

第一阶段选择方案 A。Agent MVP 跑通并出现真实重复后，再评估是否演进为方案 C。暂不直接选择方案 B，避免在原 RAG 接口内混入未稳定行为。

## 5. 原 Pipeline 各阶段的取舍

### 5.1 `loadMemory`

处理方式：保留，并在进入 loop 前只执行一次。

原因：

- Memory 是请求上下文，不是每一步 action；
- 每轮重复加载会增加数据库访问和状态不一致风险；
- Planner 每一轮都可以从 `AgentState` 读取同一份历史。

后续需要在 `AgentState` 增加历史消息时，再明确其大小限制和摘要策略。

### 5.2 `rewriteQuery`

处理方式：第一版不作为固定前置步骤，也不立即做成独立 action。

Planner 可以直接为 `RETRIEVE_KB` 产生适合检索的 `query`。当检索为空时，Planner 可以产生新的检索 query，再执行一次检索。

优化价值：

```text
固定 Pipeline：每个请求都先改写一次
Agent Loop：只有需要检索时才生成检索 query，并允许根据 observation 再改写
```

风险：Planner 可能重复产生几乎相同的 query，因此后续必须增加重复 action 检测。

### 5.3 `resolveIntents`

处理方式：MVP 阶段仍复用，但放入 `RETRIEVE_KB` adapter 内部，不再作为整个 Agent 请求的固定前置步骤。

当前 `RetrievalEngine` 依赖 `List<SubQuestionIntent>`，不能直接接收简单 query。第一版 adapter 可以执行：

```text
AgentAction.arguments.query
  -> 构造 RewriteResult 或轻量查询上下文
  -> IntentResolver.resolve(...)
  -> RetrievalEngine.retrieve(...)
```

中期应考虑为检索能力提供更直接的接口：

```java
RetrievalContext retrieve(String query, int topK)
```

该接口内部决定是否需要意图解析，避免 Agent 层了解 `SubQuestionIntent` 细节。

### 5.4 `handleGuidance`

处理方式：逐步由 `ASK_CLARIFICATION` action 替代。

固定歧义规则仍可作为 Planner 前的低成本保护层，但不能与 Planner 同时生成两套澄清结果。第一版 Agent Loop 只保留一个澄清出口：`ASK_CLARIFICATION`。

### 5.5 `handleSystemOnly`

处理方式：保留为可选快速路径，不强制进入 loop。

例如问候、能力介绍、简单系统说明，没有必要执行多轮 Agent。后续可通过轻量规则或高置信度 SYSTEM 意图直接生成回答。

这属于性能优化，不是 Agent Loop 的核心能力，MVP 可以暂不实现该快速路径。

### 5.6 `retrieve`

处理方式：从固定步骤改为可重复的 `RETRIEVE_KB` action。

优化价值：

- 不需要检索的问题可以跳过；
- 检索为空后可以换 query 重试；
- 可以在工具调用后根据真实结果补充检索；
- 可以控制总检索次数。

### 5.7 MCP 调用

处理方式：从意图节点触发改为 `CALL_MCP_TOOL` action 触发。

当前方式：

```text
IntentNode.mcpToolId
  -> McpToolRegistry
  -> 参数提取
  -> callTool
```

目标方式：

```text
AgentAction.type = CALL_MCP_TOOL
AgentAction.arguments = toolId + params
  -> McpToolRegistry
  -> McpToolExecutor
  -> AgentObservation
```

MVP 可以保留 `McpParameterExtractor` 作为参数缺失时的兼容能力，但长期应优先让 Planner 根据 Tool input schema 输出结构化参数。

### 5.8 `handleEmptyRetrieval`

处理方式：不再立即结束请求。

空检索结果应转换为 observation：

```text
success = true
content = 未检索到相关内容
metadata.empty = true
```

Planner 可以选择：

- 使用不同 query 再检索；
- 调用 MCP 工具；
- 请求用户澄清；
- 明确告知无证据并结束。

必须通过 `maxSteps`、最大检索次数和重复动作检测防止无限重试。

### 5.9 `streamRagResponse`

处理方式：从固定末尾步骤改为终止动作 `FINAL_ANSWER`。

最终回答阶段继续复用：

- `LLMService`；
- SSE `StreamCallback`；
- `StreamTaskManager`；
- 现有 prompt 组件中可复用的上下文格式化能力。

但 Agent 最终 prompt 应读取 `AgentState.steps` 中的 observations，而不是只读取单个 `RetrievalContext`。

## 6. 目标执行链路

```text
AgentChatService
  |
  +--> 加载 Memory 一次
  |
  +--> 创建 AgentState
          - originalQuestion
          - currentStep = 0
          - maxSteps
          - steps = []
  |
  v
AgentLoopRunner
  |
  +--> AgentPlanner.plan(state)
  |       |
  |       v
  |     AgentAction
  |
  +--> AgentActionExecutor.execute(action, state)
  |       |
  |       v
  |     AgentObservation
  |
  +--> new AgentStep(index, action, observation)
  |
  +--> state.steps.add(step)
  |
  +--> state.currentStep++
  |
  +--> action.type.isTerminal() ?
          |
          +-- 否 -> 下一轮 Planner
          |
          +-- 是 -> 设置 stopReason / finalAnswer 并结束
```

外部确定性停止条件：

- `currentStep >= maxSteps`；
- 最大工具调用次数；
- 最大检索次数；
- 相同 action + arguments 重复阈值；
- 总超时；
- Planner/parser 连续失败次数。

第一版只实现 `maxSteps`，其余限制在后续逐步加入。

## 7. 假设业务场景

### 7.1 简单制度问答

用户问题：

```text
员工请假需要哪些审批？
```

普通 RAG：

```text
改写 -> 意图识别 -> 检索 -> 回答
```

Agent：

```text
RETRIEVE_KB -> FINAL_ANSWER
```

该场景中 Agent 没有明显收益，反而增加一次 Planner 调用。它应作为普通 RAG 的对照场景。

### 7.2 数据与政策联合分析

用户问题：

```text
分析华东区上月销售下降原因，并结合销售政策给出建议。
```

可能的 Agent 链路：

```text
CALL_MCP_TOOL 查询区域销售数据
  -> observation：销售下降 15%
CALL_MCP_TOOL 查询产品维度
  -> observation：A 产品下降 40%
RETRIEVE_KB 查询 A 产品促销政策
  -> observation：上月促销取消
FINAL_ANSWER
```

这是 Agent Loop 的目标场景：下一步依赖上一步真实结果，无法完全预先写死。

### 7.3 首次检索为空

用户问题：

```text
新员工保护期怎么申请？
```

可能的 Agent 链路：

```text
RETRIEVE_KB query=新员工保护期
  -> observation：空
RETRIEVE_KB query=新员工试用期权益与申请流程
  -> observation：命中
FINAL_ANSWER
```

优化点：原 Pipeline 在首次检索为空后直接结束，Loop 可以根据 observation 改写后重试。

### 7.4 缺少工具必填参数

用户问题：

```text
帮我查一下销售额。
```

工具需要 `region` 和 `month`。Planner 应输出：

```text
ASK_CLARIFICATION
  question=请提供区域和月份
```

该场景验证 Planner 是否能识别工具 schema 的必填参数，而不是编造默认参数。

### 7.5 工具失败

```text
CALL_MCP_TOOL
  -> observation.success=false
  -> observation.errorMessage=timeout
```

Planner 可以选择降级到 KB、换工具或结束。对有副作用的工具，重试前必须考虑幂等性。

## 8. 当前 Domain 设计

### 8.1 `AgentActionType`

第一版动作：

```text
RETRIEVE_KB
CALL_MCP_TOOL
ASK_CLARIFICATION
FINAL_ANSWER
```

终止性只由 action type 决定：

```java
public boolean isTerminal() {
    return this == FINAL_ANSWER || this == ASK_CLARIFICATION;
}
```

不再使用额外 `finish` 字段，避免两个事实来源互相矛盾。

### 8.2 `AgentAction`

```text
type
thought
arguments
```

`thought` 只保存可公开的简短决策说明，不保存或要求模型输出完整思维链。

### 8.3 `AgentObservation`

```text
actionType
success
content
metadata
errorMessage
```

`content` 给后续 Planner 和最终回答使用，`metadata` 用于 trace、调试和溯源。

### 8.4 `AgentStep`

```text
stepIndex
action
observation
```

执行成功和错误只保存在 observation 中，避免 `AgentStep` 重复保存相同事实。

### 8.5 `AgentState`

```text
conversationId
userId
originalQuestion
currentStep
maxSteps
steps
finalAnswer
stopReason
errorMessage
```

约定：

- `currentStep` 从 0 开始；
- 每完成并记录一个 `AgentStep` 后加 1；
- 循环级错误写入 `AgentState.errorMessage`；
- action 执行错误写入 `AgentObservation.errorMessage`。

## 9. AgentActionParser 契约

输入：LLM 返回的字符串。

输出：合法 `AgentAction`。

已覆盖：

- 合法 JSON；
- 空输入；
- 非法 JSON；
- 未知 action type；
- Markdown JSON 代码块。

Parser 对外统一抛出 `IllegalArgumentException`，不让调用方依赖 Gson 异常类型。

## 10. Planner 第一版设计

### 10.1 Planner 的职责

Planner 只负责：

```text
读取当前 AgentState
  -> 选择下一步 AgentAction
```

Planner 不负责：

- 修改 `AgentState`；
- 执行知识库检索；
- 执行 MCP 工具；
- 向 SSE 写内容；
- 判断循环是否超过 `maxSteps`；
- 持久化 trace。

这些职责分别属于 Runner、Executor、输出层和 Trace 层。

### 10.2 接口

建议放置：

```text
bootstrap/src/main/java/com/koawa/agent/agent/planner/AgentPlanner.java
```

第一版接口：

```java
public interface AgentPlanner {

    AgentAction plan(AgentState state);
}
```

设计理由：

- 输入只有一个完整状态对象；
- 输出只有一个动作；
- Planner 不直接修改状态；
- Scripted 和 LLM 实现可以使用同一接口；
- 异常交给 `AgentLoopRunner` 统一捕获并转换为 `AgentStopReason.ERROR`。

### 10.3 为什么先做 Scripted Planner

建议放置：

```text
bootstrap/src/main/java/com/koawa/agent/agent/planner/ScriptedAgentPlanner.java
```

它保存一组预先定义的 actions，并按顺序返回：

```text
第 1 次 plan -> RETRIEVE_KB
第 2 次 plan -> FINAL_ANSWER
```

目的：

- 不依赖 LLM API Key；
- 不依赖 prompt 稳定性；
- 可以只验证 loop 的状态推进和停止条件；
- 失败时能明确判断是 loop 问题还是模型输出问题。

第一版行为：

```text
有剩余 action -> 返回下一个
没有剩余 action -> 抛 IllegalStateException
```

`ScriptedAgentPlanner` 不注册为默认 Spring Bean，避免测试实现意外进入正式业务。第一版可以作为普通类，由测试或装配代码显式创建。

#### Scripted Planner 状态方案

选择无内部可变游标的方案：

```text
ScriptedAgentPlanner 保存不可变 List<AgentAction>
  -> 读取 state.currentStep
  -> 返回 actions.get(currentStep)
```

不使用内部 `Queue.remove()` 或自增索引，原因是：

- Planner 自身不修改状态；
- 同一个 state 重复 plan 时返回相同 action，行为可重现；
- 不会因为 Planner Bean 被并发复用而串扰不同请求；
- step 的所有权仍属于 `AgentLoopRunner`；
- 测试可以直接通过设置 `state.currentStep` 验证不同步骤。

边界规则：

```text
state 为 null -> 参数错误
currentStep < 0 -> 参数错误
currentStep >= actions.size -> IllegalStateException
有效索引 -> 返回对应 action
```

构造时使用 `List.copyOf(actions)` 保存脚本，避免调用方后续修改原列表影响 Planner 行为。

### 10.4 未来 LLM Planner

未来实现：

```text
LlmAgentPlanner
  -> 构建 system/user messages
  -> LLMService.chat
  -> AgentActionParser.parse
  -> AgentAction
```

Planner system message 包含：

- Planner 角色和约束；
- 可用 action 类型；
- action JSON schema；
- 停止规则；
- 禁止编造 observation。

Planner user message 包含：

- 用户原始问题；
- 当前 step 和 maxSteps；
- 历史 `AgentStep` 摘要；
- 可用 MCP tools 摘要；
- 最近错误或空结果。

## 11. 第一阶段实现顺序

```text
1. AgentPlanner 接口
2. ScriptedAgentPlanner
3. ScriptedAgentPlanner 单元测试
4. AgentLoopRunner
5. Fake AgentActionExecutor
6. AgentLoopRunner 单元测试
7. RETRIEVE_KB adapter
8. CALL_MCP_TOOL adapter
9. LlmAgentPlanner
10. SSE / Trace 接入
```

## 12. 验证标准

在没有真实业务场景时，先使用假设场景建立小型评估集。

普通 RAG 与 Agent 模式至少比较：

- 是否完成任务；
- 是否引用到正确证据；
- 工具调用次数；
- 重复检索次数；
- 总步骤数；
- 延迟；
- Token 成本；
- 失败后是否能从 trace 定位原因。

Agent 模式不能只因为“多调用了工具”就被认为更好。对于简单问答，如果结果相同但成本和延迟更高，应继续使用普通 RAG。

## 13. 当前明确不做

- 不删除 `StreamChatPipeline`；
- 不直接改原 `/rag/v3/chat` 行为；
- 不先接真实 LLM Planner；
- 不先实现多 Agent；
- 不引入 LangGraph；
- 不重写 `RetrievalEngine`；
- 不先解决全部停止策略；
- 不在没有评估数据时默认所有问题走 Agent。

## 14. 下一步

当前下一步只实现：

```text
AgentPlanner
ScriptedAgentPlanner
```

验证目标：

```text
给定两个预设 action，连续调用 plan(state) 时按顺序返回；
actions 用尽后明确失败；
Planner 不修改 AgentState。
```
