# Current RAG Chain Notes

Day: 1

Purpose: reference notes for checking Day 1 understanding. Do not treat this as a replacement for manually reading the code and drawing the flow.

Use this file as:

- a review checklist after reading the code yourself
- a correction reference after drawing your own sequence diagram
- a recovery note if chat context is lost

Do not use this file as:

- a substitute for reading source code
- the only Day 1 learning output
- an implementation plan to execute blindly

## 1. High-Level Runtime Chain

Current user-facing streaming endpoint:

```text
GET /rag/v3/chat
```

Current chain:

```text
RAGChatController.chat
  -> RAGChatServiceImpl.streamChat
    -> StreamCallbackFactory.createChatEventHandler
    -> ChatQueueLimiter.enqueue
    -> StreamChatTraceRunner.run
    -> StreamChatPipeline.execute
      -> loadMemory
      -> rewriteQuery
      -> resolveIntents
      -> handleGuidance
      -> handleSystemOnly
      -> RetrievalEngine.retrieve
      -> handleEmptyRetrieval
      -> streamRagResponse
        -> RAGPromptService.buildStructuredMessages
        -> LLMService.streamChat
        -> StreamChatEventHandler
          -> SSE message events
          -> append assistant message to memory
```

This is a fixed pipeline, not an agent loop.

## 2. Controller Layer

File:

```text
bootstrap/src/main/java/com/koawa/agent/rag/controller/RAGChatController.java
```

Responsibility:

- Exposes `GET /rag/v3/chat`.
- Creates `SseEmitter` using configured timeout.
- Delegates all real work to `RAGChatService.streamChat`.
- Exposes `POST /rag/v3/stop` for stream cancellation.
- Uses `@IdempotentSubmit` to prevent repeated chat submission from the same user.

Important observation:

- Controller is thin and should stay thin.
- Agent mode should likely be a new endpoint or a feature flag, not controller-heavy logic.

## 3. Service Entry Layer

File:

```text
bootstrap/src/main/java/com/koawa/agent/rag/service/impl/RAGChatServiceImpl.java
```

Responsibility:

- Resolves `conversationId`, creating one if missing.
- Creates `taskId`.
- Creates stream callback through `StreamCallbackFactory`.
- Enqueues request through `ChatQueueLimiter`.
- Wraps execution with `StreamChatTraceRunner`.
- Builds `StreamChatContext`.
- Calls `StreamChatPipeline.execute(ctx)`.
- Cancels stream through `StreamTaskManager`.

Important observation:

- This class is the best place to route between normal RAG mode and future agent mode.
- It already has task id, conversation id, user id, callback, trace wrapper, and queue protection.
- A future `AgentChatService` can mirror this shape instead of replacing it.

## 4. Stream Callback And SSE Output

Files:

```text
bootstrap/src/main/java/com/koawa/agent/rag/service/handler/StreamCallbackFactory.java
bootstrap/src/main/java/com/koawa/agent/rag/service/handler/StreamChatEventHandler.java
```

Responsibility:

- Sends initial `META` SSE event with `conversationId` and `taskId`.
- Streams answer chunks as `MESSAGE` events.
- Streams thinking chunks separately when provider returns thinking content.
- On complete:
  - appends assistant message to conversation memory
  - emits `FINISH`
  - emits `DONE`
  - unregisters task
  - completes emitter
- On cancel:
  - persists partial answer if present
  - builds completion payload

Important observation:

- The callback is reusable for agent mode.
- Agent mode can stream final answer through the same callback.
- If intermediate agent steps need to be shown in UI, either add new SSE event types or encode them as trace-only first.

## 5. Trace Layer

File:

```text
bootstrap/src/main/java/com/koawa/agent/rag/trace/StreamChatTraceRunner.java
```

Responsibility:

- Creates one trace run with name `rag-stream-chat`.
- Sets `RagTraceContext` and task id.
- Wraps callback with `ForwardingStreamCallback`.
- Records user-perceived first packet node.
- Finishes trace run on callback finish/error.

Important observation:

- Agent mode should not bypass this. It should either:
  - reuse `StreamChatTraceRunner` with a different trace name, or
  - introduce `AgentStreamTraceRunner`.
- Each agent loop step should become a trace node:
  - `agent-plan`
  - `agent-action-retrieve`
  - `agent-action-mcp`
  - `agent-observation`
  - `agent-final-answer`

## 6. Pipeline Layer

File:

```text
bootstrap/src/main/java/com/koawa/agent/rag/service/pipeline/StreamChatPipeline.java
```

Current stages:

```text
loadMemory
rewriteQuery
resolveIntents
handleGuidance
handleSystemOnly
retrieve
handleEmptyRetrieval
streamRagResponse
```

### 6.1 loadMemory

Uses:

```text
ConversationMemoryService.loadAndAppend
```

Behavior:

- Loads prior memory.
- Appends current user message.
- Stores result in `StreamChatContext.history`.

Agent implication:

- Agent mode needs the same memory, but the agent state should explicitly keep:
  - original question
  - history
  - observations
  - final answer

### 6.2 rewriteQuery

Uses:

```text
QueryRewriteService.rewriteWithSplit
```

Behavior:

- Normalizes terms.
- Calls LLM for rewrite and sub-question split when enabled.
- Falls back to rule-based split when rewrite disabled or failed.

Agent implication:

- Keep this as pre-processing before agent loop.
- Later, rewrite can become an explicit `QUERY_REWRITE` action, but not in MVP.

### 6.3 resolveIntents

Uses:

```text
IntentResolver.resolve
```

Behavior:

- Classifies each sub-question in parallel.
- Keeps intents above min score.
- Caps total intents.

Agent implication:

- Intent results can be planner context.
- Current intent system is useful for routing, but it is not the same as an agent planner.

### 6.4 handleGuidance

Uses:

```text
IntentGuidanceService.detectAmbiguity
```

Behavior:

- If ambiguity is detected, sends clarification/guidance prompt and completes stream.

Agent implication:

- This maps well to future `ASK_CLARIFICATION`.
- In agent mode, ambiguity should be an action, not only a fixed pre-answer branch.

### 6.5 handleSystemOnly

Behavior:

- If all recognized intents are system-only, skips retrieval.
- Streams direct system response using custom intent prompt or default chat prompt.

Agent implication:

- This maps to a future `FINAL_ANSWER` with no retrieval, or a system action.

### 6.6 retrieve

Uses:

```text
RetrievalEngine.retrieve
```

Behavior:

- Retrieves KB context and MCP context from sub-question intents.
- Returns `RetrievalContext`.

Agent implication:

- This is the key capability to wrap as an agent action.
- For MVP, create action `RETRIEVE_KB` that can call this or a lower-level retriever.

### 6.7 streamRagResponse

Uses:

```text
RAGPromptService.buildStructuredMessages
LLMService.streamChat
```

Behavior:

- Merges intent groups.
- Builds prompt according to KB-only, MCP-only, or mixed evidence.
- Streams final answer.

Agent implication:

- This maps to future `FINAL_ANSWER`.
- In agent mode, final answer should use all accumulated observations, not only one retrieval context.

## 7. Query Rewrite Layer

File:

```text
bootstrap/src/main/java/com/koawa/agent/rag/core/rewrite/MultiQuestionRewriteService.java
```

Responsibility:

- Term mapping normalization.
- LLM rewrite and sub-question split.
- JSON parsing of LLM output.
- Rule-based fallback.

Important observation:

- Good reusable pre-agent component.
- For agent MVP, do not change it.
- Later, expose it as optional action if the planner needs to reformulate a failed query.

## 8. Intent Layer

Files:

```text
bootstrap/src/main/java/com/koawa/agent/rag/core/intent/IntentResolver.java
bootstrap/src/main/java/com/koawa/agent/rag/core/intent/DefaultIntentClassifier.java
```

Responsibility:

- Load intent tree from cache/DB.
- Ask LLM to score intent nodes.
- Separate KB, MCP, and SYSTEM intents.
- Limit intent count.

Important observation:

- This is "semantic routing", not full planning.
- It decides where a question belongs, not what multi-step strategy to execute.
- Agent planner can use intent results as context, but should own step-by-step decisions.

## 9. Retrieval Layer

Files:

```text
bootstrap/src/main/java/com/koawa/agent/rag/core/retrieve/RetrievalEngine.java
bootstrap/src/main/java/com/koawa/agent/rag/core/retrieve/MultiChannelRetrievalEngine.java
```

Current behavior:

- `RetrievalEngine` splits selected intents into KB and MCP groups.
- KB path calls `MultiChannelRetrievalEngine`.
- MCP path extracts parameters and calls MCP tools.
- Multiple sub-questions are handled in parallel.
- Final context is formatted into KB context and MCP context.

`MultiChannelRetrievalEngine` behavior:

- Builds search context.
- Finds enabled search channels.
- Executes channels in parallel.
- Applies post-processors such as dedup/rerank.

Important observation:

- Current MCP execution happens inside retrieval orchestration.
- For agent loop, MCP should become its own action rather than being hidden inside retrieval.
- Do not delete current behavior. It is still useful for normal RAG and mixed mode.

## 10. Prompt Layer

File:

```text
bootstrap/src/main/java/com/koawa/agent/rag/core/prompt/RAGPromptService.java
```

Responsibility:

- Selects prompt scene:
  - KB-only
  - MCP-only
  - mixed
- Builds message list:
  - system prompt
  - history
  - evidence + question as user message

Important observation:

- Agent final answer should reuse this idea but use `AgentObservation` as evidence source.
- A new `AgentPromptBuilder` is cleaner than forcing agent-specific state into `RAGPromptService`.

## 11. Memory Layer

File:

```text
bootstrap/src/main/java/com/koawa/agent/rag/core/memory/DefaultConversationMemoryService.java
```

Responsibility:

- Loads summary and history in parallel.
- Appends new messages.
- Compresses when needed.

Important observation:

- Memory currently stores chat messages, not agent step state.
- Future agent steps should probably be stored in trace first, not conversation memory.
- Conversation memory should receive user message and final assistant answer only.

## 12. Current Chain Diagram

```text
Client
  |
  v
RAGChatController
  |
  v
RAGChatServiceImpl
  |
  +-- create conversationId/taskId
  +-- create StreamChatEventHandler
  +-- enqueue by ChatQueueLimiter
  +-- wrap with StreamChatTraceRunner
  |
  v
StreamChatPipeline
  |
  +-- ConversationMemoryService
  +-- QueryRewriteService
  +-- IntentResolver
  +-- IntentGuidanceService
  +-- RetrievalEngine
        |
        +-- MultiChannelRetrievalEngine
        |     +-- SearchChannel(s)
        |     +-- SearchResultPostProcessor(s)
        |
        +-- MCP parameter extraction/tool execution
  |
  +-- RAGPromptService
  +-- LLMService.streamChat
  |
  v
StreamChatEventHandler
  |
  +-- SSE MESSAGE/FINISH/DONE
  +-- persist assistant answer
```

## 13. Where To Insert Agent Loop

Recommended first path:

```text
RAGChatServiceImpl
  -> if normal mode: StreamChatPipeline
  -> if agent mode: AgentStreamChatPipeline or AgentChatService
```

Agent mode should reuse:

- `ConversationMemoryService`
- `QueryRewriteService`
- `IntentResolver` as optional planner context
- `RetrievalEngine` or lower-level retrievers
- `McpToolRegistry` / `McpToolExecutor`
- `RAGPromptService` concepts
- `LLMService`
- `StreamCallback`
- `StreamTaskManager`
- trace infrastructure

Do not insert the whole agent loop inside:

- `RAGChatController`
- `RetrievalEngine`
- `RAGPromptService`
- `StreamChatEventHandler`

Reason:

- Controller should remain thin.
- Retrieval should remain a capability, not orchestration owner.
- Prompt service should build messages, not manage loop state.
- Stream callback should handle output, not decision-making.

## 14. Day 1 Findings

1. Current system is a strong static RAG pipeline.
2. The chain already has enough reusable components for agentic redevelopment.
3. The main missing abstraction is not another retriever; it is explicit agent state and loop control.
4. MCP is currently coupled into retrieval. Agent mode should separate MCP as an action.
5. Trace is already valuable and should become the main debugging surface for agent steps.
6. Conversation memory should store final conversation, while trace should store intermediate agent steps.
7. Several comments/log strings appear garbled in terminal output, likely due to encoding mismatch. Avoid broad cleanup now; fix only if touching affected files.

## 15. Next Step

Phase 1, Day 2:

Map the retrieval internals in detail:

- `RetrievalEngine`
- `MultiChannelRetrievalEngine`
- `SearchChannel`
- `VectorGlobalSearchChannel`
- `IntentDirectedSearchChannel`
- `RerankPostProcessor`
- `DeduplicationPostProcessor`
- `MilvusRetrieverService`
- `PgRetrieverService`

Goal:

Decide whether the first `RETRIEVE_KB` agent action should call `RetrievalEngine` directly or a lower-level retriever abstraction.
