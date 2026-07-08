# Day 01 Learning Task

Goal: understand the current chat pipeline by reading and drawing, not by changing business code.

## 1. Today's Rule

Do not write production code today.

Today is for:

- READ-ONLY: read the current RAG chat path.
- DRAW: draw the current sequence.
- EXPLAIN: explain each stage in your own words.
- QUESTION: identify where agent loop should be inserted.

## 2. Files To Read By Hand

Read in this order:

```text
bootstrap/src/main/java/com/koawa/agent/rag/controller/RAGChatController.java
bootstrap/src/main/java/com/koawa/agent/rag/service/RAGChatService.java
bootstrap/src/main/java/com/koawa/agent/rag/service/impl/RAGChatServiceImpl.java
bootstrap/src/main/java/com/koawa/agent/rag/service/pipeline/StreamChatContext.java
bootstrap/src/main/java/com/koawa/agent/rag/service/pipeline/StreamChatPipeline.java
bootstrap/src/main/java/com/koawa/agent/rag/service/handler/StreamCallbackFactory.java
bootstrap/src/main/java/com/koawa/agent/rag/service/handler/StreamChatEventHandler.java
bootstrap/src/main/java/com/koawa/agent/rag/trace/StreamChatTraceRunner.java
```

Optional after the above:

```text
bootstrap/src/main/java/com/koawa/agent/rag/core/rewrite/MultiQuestionRewriteService.java
bootstrap/src/main/java/com/koawa/agent/rag/core/intent/IntentResolver.java
bootstrap/src/main/java/com/koawa/agent/rag/core/retrieve/RetrievalEngine.java
bootstrap/src/main/java/com/koawa/agent/rag/core/prompt/RAGPromptService.java
```

## 3. What To Mark While Reading

For each file, write down:

```text
1. This class receives what input?
2. This class produces what output?
3. Does this class decide business flow, or only adapt/transport data?
4. Does this class call LLM?
5. Does this class touch memory?
6. Does this class touch trace?
7. Would agent loop belong here? Why or why not?
```

## 4. Diagram To Draw

Draw one sequence diagram with these nodes:

```text
Client
RAGChatController
RAGChatServiceImpl
ChatQueueLimiter
StreamChatTraceRunner
StreamChatPipeline
ConversationMemoryService
QueryRewriteService
IntentResolver
IntentGuidanceService
RetrievalEngine
RAGPromptService
LLMService
StreamChatEventHandler
```

The diagram should show:

- SSE request starts at controller.
- `conversationId` and `taskId` are created in service.
- Queue and trace wrap execution before pipeline.
- Pipeline performs memory, rewrite, intent, guidance, retrieval, prompt, stream.
- Stream callback writes chunks to frontend and persists final assistant message.

## 5. Concepts To Learn Today

### Thin Controller

`RAGChatController` should only handle HTTP/SSE details and delegate.

Interview explanation:

```text
The controller does not own RAG logic. It creates the SSE emitter and delegates to service.
```

### Service As Orchestration Entry

`RAGChatServiceImpl` creates runtime ids, callback, queue, trace, and pipeline context.

Interview explanation:

```text
This is the runtime entry point for one chat task. It is a good place to route normal RAG mode versus future agent mode.
```

### Pipeline As Fixed Flow

`StreamChatPipeline` is a deterministic pipeline.

Interview explanation:

```text
The existing system is not a full agent loop because the LLM does not repeatedly decide the next action after observing intermediate results.
```

### Stream Callback As Output Adapter

`StreamChatEventHandler` is responsible for SSE output and final persistence.

Interview explanation:

```text
The callback should not contain agent decisions. It only transports streamed content and records the final assistant answer.
```

### Trace Runner

`StreamChatTraceRunner` wraps the request and records run lifecycle.

Interview explanation:

```text
Agent steps should become trace nodes so the loop is debuggable.
```

## 6. Questions You Should Answer Before Day 2

Answer these in your own words:

1. Why is the current pipeline not an agent loop?
2. What is the difference between `RAGChatServiceImpl` and `StreamChatPipeline`?
3. Why should agent loop not be placed inside `RAGChatController`?
4. Why should agent loop not be placed inside `RetrievalEngine`?
5. Which current classes can be reused by agent mode?
6. What should be persisted to conversation memory, and what should go to trace?
7. If the user clicks stop, which components participate in cancellation?

## 7. Small Hand Exercise

Do not code. Write this as notes:

```text
Normal RAG mode:
input:
steps:
output:

Future agent mode:
input:
steps:
output:

Main difference:
```

## 8. Day 1 Acceptance Criteria

You are done with Day 1 when:

- You can draw the sequence diagram without looking at this file.
- You can explain the pipeline in 3 minutes.
- You can point to the best insertion point for agent mode.
- You can explain why `RetrievalEngine` should be a capability used by the agent, not the owner of the agent loop.

## 9. Recommended Next Prompt To Assistant

After you read and draw, ask:

```text
我完成了 Day 1 阅读和图，请你用面试官方式问我 8 个问题检查理解。
```

Or:

```text
这是我画的 Day 1 链路图/文字版，请你 review 哪里理解错了。
```

