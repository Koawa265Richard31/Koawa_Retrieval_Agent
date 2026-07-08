# Agentic RAG 55 Day Learning And Redevelopment Plan

> Purpose: this document is the long-context recovery anchor for the `D:\koawa-agent` project. If the chat context is lost, reread this file first, then continue from the current day/checkpoint.

## 1. Current Project Baseline

This project is already more than a simple RAG demo. Without relying on README, the codebase currently has:

- Java/Spring Boot multi-module backend: `bootstrap`, `infra-ai`, `framework`, `mcp-server`.
- React/Vite admin frontend under `frontend`.
- RAG pipeline: query rewrite, query split, intent classification, retrieval, rerank, prompt assembly, streaming answer.
- Vector stores: Milvus and pgvector.
- Model infrastructure: chat, embedding, rerank clients, provider routing, fallback, model health.
- MCP integration: MCP client executor plus a local `mcp-server` module with sample tools.
- Knowledge ingestion: source fetch, parse, enhance, chunk, enrich, index pipeline.
- Observability: RAG trace run/node tables and trace support.
- Conversation memory: message history plus summary storage.
- Admin capabilities: knowledge base, chunks, intent tree, ingestion, trace, model/settings pages.

Main gap: the project is currently closer to "agentic routing RAG" than a full agentic RAG framework. The existing flow is mostly:

```text
query rewrite -> intent classify -> retrieve / call MCP once -> build context -> answer
```

The missing target flow is:

```text
plan/think -> choose action -> execute retrieval/tool -> observe -> decide continue or answer -> final answer
```

## 2. 55 Day Goal

In 55 days, rebuild the project into a learning-grade and interview-ready agentic RAG system:

1. Re-understand and refactor the existing RAG chain using this project, not a separate demo.
2. Add a real agent loop layer without deleting the current RAG pipeline.
3. Introduce framework-level concepts missing from the current implementation: agent state, step trace, planner, action schema, tool registry, observation, stop policy, retry, reflection/evaluation.
4. Keep the Java/Spring version working as the primary implementation.
5. Extract language-agnostic architecture so the same system can later be rebuilt in Python or TypeScript.
6. Produce runnable checkpoints, tests, trace evidence, and learning notes.

The end state should be explainable as:

```text
Enterprise Agentic RAG Platform
= RAG retrieval engine
+ MCP/tool execution
+ agent loop
+ ingestion pipeline
+ observability
+ model routing
+ evaluation and safety
```

## 2.1 Learning Mode Rules

This plan is for guided learning, not for fully delegated development.

Default division of work:

- The learner manually reads core production code.
- The learner manually draws architecture diagrams.
- The learner manually writes the first version of new core abstractions.
- The assistant explains, reviews, asks questions, points out risks, and only patches code when explicitly asked.
- The assistant may create or update planning/notes documents when asked.
- The assistant should not silently implement the project roadmap.

Use three labels for every task:

```text
HAND-CODE: code the learner should type by hand.
READ-ONLY: code/config the learner should inspect and explain.
DRAW: architecture or sequence diagram the learner should draw.
```

Optional fourth label:

```text
ASSISTED-CODE: code the assistant may help implement after the learner has tried or explicitly asks.
```

Learning cycle for each topic:

```text
1. READ-ONLY: inspect existing code.
2. DRAW: draw the current flow.
3. EXPLAIN: explain it in your own words.
4. HAND-CODE: implement a small related piece.
5. REVIEW: assistant reviews correctness and design.
6. VERIFY: run targeted checks.
7. RETROSPECT: write what changed in understanding.
```

## 3. Target Architecture

Add one new orchestration layer above the existing RAG/MCP capabilities.

```text
User Question
  |
  v
Conversation Memory Loader
  |
  v
AgentLoopRunner
  |
  +-- AgentPlanner: asks LLM for next structured action
  |
  +-- AgentActionExecutor
        |
        +-- RETRIEVE_KB -> existing RetrievalEngine / RetrieverService
        +-- CALL_MCP_TOOL -> existing McpToolRegistry / McpToolExecutor
        +-- ASK_CLARIFICATION -> stream clarification
        +-- FINAL_ANSWER -> answer generation
  |
  +-- AgentObservationStore / Trace
  |
  v
Final Answer
```

Keep the existing `RetrievalEngine` as a tool/capability. Do not rewrite it into the agent loop at first.

## 4. Core New Concepts To Implement

### 4.1 Agent State

Create a state object that survives across loop steps.

Suggested package:

```text
bootstrap/src/main/java/com/koawa/agent/agent
```

Suggested classes:

- `AgentState`
- `AgentStep`
- `AgentAction`
- `AgentActionType`
- `AgentObservation`
- `AgentLoopResult`
- `AgentStopReason`

Minimum fields:

- original question
- rewritten question
- conversation id
- user id
- history
- current step index
- max steps
- actions taken
- observations
- final answer
- error
- trace id

### 4.2 Action Types

Start with a small action vocabulary:

```text
RETRIEVE_KB
CALL_MCP_TOOL
FINAL_ANSWER
ASK_CLARIFICATION
```

Add later if needed:

```text
REFLECT
QUERY_REWRITE
MEMORY_LOOKUP
```

### 4.3 Planner Output Contract

The LLM planner must output structured JSON. Avoid free-form text.

Example:

```json
{
  "thought": "The user asks for HR policy details. Knowledge retrieval is needed.",
  "action": "RETRIEVE_KB",
  "arguments": {
    "query": "员工薪资与福利政策",
    "topK": 5
  },
  "finish": false
}
```

Final answer action:

```json
{
  "thought": "The retrieved evidence is enough.",
  "action": "FINAL_ANSWER",
  "arguments": {
    "answerStyle": "concise"
  },
  "finish": true
}
```

### 4.4 Loop Policy

Use strict controls:

- Max loop steps: 3 during development, 5 after stable.
- Max tool calls per answer: 3 initially.
- Stop if the same action repeats with materially same arguments.
- Stop if planner JSON fails twice.
- Stop if all observations are empty or errors.
- Always allow `FINAL_ANSWER` if enough context exists.

### 4.5 Trace Integration

Every agent step should be visible in the existing trace system:

- root: `agent-stream-chat`
- nodes:
  - `agent-plan`
  - `agent-action-retrieve`
  - `agent-action-mcp`
  - `agent-observation`
  - `agent-final-answer`

This is important because trace will prove the agent loop is actually working.

## 5. 55 Day Timeline

The plan is split into 8 phases. Each phase has a concrete output.

### Phase 1: Repo Reorientation And RAG Refresh, Days 1-5

Goal: understand the current project deeply enough to change it safely.

Tasks:

- READ-ONLY: map the current chat path from controller to stream output.
- READ-ONLY: map query rewrite, intent classification, retrieval, rerank, prompt build.
- READ-ONLY: map model routing and provider fallback.
- READ-ONLY: map MCP client/server integration.
- READ-ONLY: map ingestion pipeline nodes.
- DRAW: draw one sequence diagram for the chat pipeline.
- DRAW: draw one module dependency diagram for RAG, infra-ai, framework, mcp-server.
- EXPLAIN: write a short explanation of each stage in your own words.
- VERIFY: run only necessary lightweight tests or targeted compile checks.

Deliverables:

- `docs/current-rag-chain-notes.md`
- A sequence diagram of current RAG flow.
- A list of current extension points.

Acceptance:

- You can explain how a user question becomes a streamed answer.
- You can explain where to insert agent loop without breaking current RAG.

### Phase 2: Agent Loop Design And Contracts, Days 6-10

Goal: design the new agent layer before coding.

Tasks:

- DRAW: current static RAG pipeline vs target agent loop.
- READ-ONLY: inspect `RetrievalEngine`, MCP executor, trace classes before designing.
- HAND-CODE: define `AgentState`, `AgentStep`, `AgentAction`, `AgentObservation`.
- HAND-CODE: define `AgentActionType`.
- HAND-CODE: write planner JSON schema classes or DTOs.
- HAND-CODE: write parser tests for valid/invalid planner JSON.
- EXPLAIN: define stop policy and error policy in notes before coding the runner.
- DRAW: trace node naming and parent-child relation.

Deliverables:

- `docs/agent-loop-design.md`
- Empty or skeleton Java package for `agent`.
- Unit tests for JSON parsing of planner output.

Acceptance:

- The design can answer: "What happens if the planner emits invalid JSON?"
- The design can answer: "How do we prevent infinite loops?"

### Phase 3: Minimal Agent Loop MVP, Days 11-18

Goal: make a real loop run end to end with one retrieval action and final answer.

Tasks:

- HAND-CODE: implement `AgentPlanner` interface and one LLM-backed implementation.
- HAND-CODE: implement `AgentLoopRunner` with max step policy.
- HAND-CODE: implement `AgentActionExecutor`.
- HAND-CODE: implement `RETRIEVE_KB`.
- HAND-CODE: implement `FINAL_ANSWER`.
- ASSISTED-CODE: endpoint/feature flag wiring if Spring details slow progress.
- READ-ONLY: compare behavior with original `/rag/v3/chat`.
- VERIFY: keep the original `/rag/v3/chat` behavior intact unless explicitly switched.

Deliverables:

- Agent mode can answer a KB question using loop steps.
- Trace shows plan -> retrieve -> final answer.
- At least 3 focused tests.

Acceptance:

- A single question produces at least one persisted/visible agent step.
- The loop stops correctly.
- Existing normal RAG still works.

### Phase 4: MCP Tool Loop, Days 19-25

Goal: make the agent choose and call MCP tools dynamically.

Tasks:

- READ-ONLY: inspect `McpToolRegistry`, `McpToolExecutor`, `McpClientToolExecutor`, and `mcp-server` tools.
- DRAW: MCP client/server/tool call sequence.
- HAND-CODE: add `CALL_MCP_TOOL` action.
- HAND-CODE: expose available tools to planner with names, descriptions, schemas.
- HAND-CODE: convert tool results/errors into `AgentObservation`.
- ASSISTED-CODE: timeout/cancellation details if needed.

Deliverables:

- Agent can choose between KB retrieval and MCP tool call.
- Agent can combine MCP result with KB result before final answer.
- Trace shows tool action and observation.

Acceptance:

- Example: weather/ticket/sales tool call works through agent loop.
- Tool failure does not crash the whole answer.

### Phase 5: Reflection, Clarification, And Answer Quality, Days 26-32

Goal: add practical agentic behavior beyond one-pass tool use.

Tasks:

- DRAW: decision tree for answer/continue/clarify.
- HAND-CODE: add `ASK_CLARIFICATION` action.
- HAND-CODE: add optional `REFLECT` action or internal answer sufficiency checker.
- HAND-CODE: add evidence sufficiency rule:
  - answer if context is enough
  - retrieve again if context is partial
  - clarify if user intent is ambiguous
- HAND-CODE: add final answer grounding prompt.
- ASSISTED-CODE: citation/evidence formatting if current UI support is unclear.

Deliverables:

- Agent asks clarification for ambiguous questions.
- Agent can do second retrieval when first retrieval is insufficient.
- Agent avoids unsupported final answers when evidence is weak.

Acceptance:

- At least 5 scenario tests:
  - pure KB
  - pure MCP
  - KB + MCP
  - ambiguous question
  - no useful evidence

### Phase 6: Ingestion And Knowledge Quality, Days 33-39

Goal: connect agentic RAG quality to ingestion quality.

Tasks:

- READ-ONLY: review ingestion pipeline nodes: fetcher, parser, enhancer, chunker, enricher, indexer.
- DRAW: ingestion pipeline from source to vector store.
- EXPLAIN: how chunk quality affects retrieval and agent decisions.
- HAND-CODE: improve one weak area only after inspection:
  - chunk metadata
  - parent-child chunk relation
  - heading-aware chunking
  - document-level summary
  - generated questions per chunk
- VERIFY: ensure ingestion output improves retrieval.
- ASSISTED-CODE: trace/log evidence if the current trace hooks are complex.

Deliverables:

- One concrete ingestion quality improvement.
- Before/after retrieval example.
- Notes explaining why retrieval improved.

Acceptance:

- Same question retrieves better chunks after ingestion change.

### Phase 7: Evaluation, Observability, And Safety, Days 40-47

Goal: make the system measurable and safer.

Tasks:

- HAND-WRITE: create a small golden dataset of 20-30 questions.
- HAND-CODE: add evaluation endpoint or test runner.
- HAND-CODE: track:
  - retrieval hit quality
  - answer groundedness
  - tool success/failure
  - latency
  - loop step count
- HAND-CODE: add tool allowlist.
- HAND-CODE: add max tool calls and timeout.
- EXPLAIN: write sensitive argument logging policy before implementation.

Deliverables:

- `docs/evaluation-plan.md`
- A runnable eval script/test/controller.
- Safety rules for tool execution.

Acceptance:

- You can compare normal RAG vs agentic RAG on the same questions.
- You can show where agent mode improves or worsens results.

### Phase 8: Packaging, Review, And Migration Readiness, Days 48-55

Goal: finish with an interview-ready and migration-ready project.

Tasks:

- READ-ONLY: review all touched package boundaries.
- HAND-WRITE: write architecture notes.
- DRAW: final diagrams.
- HAND-WRITE: record known limitations.
- HAND-WRITE: prepare migration guide for Python/TypeScript ecosystems.
- VERIFY: run final targeted verification.

Deliverables:

- `docs/final-agentic-rag-architecture.md`
- `docs/language-ecosystem-migration-guide.md`
- Final demo script.
- Final limitations and future roadmap.

Acceptance:

- You can demo:
  - normal RAG
  - agentic RAG
  - MCP tool call
  - multi-step loop
  - trace view
  - evaluation result
- You can explain how to rebuild the same design in another language.

## 6. Weekly Learning Focus

### Week 1

Focus: current RAG system.

Questions to answer:

- What is the exact runtime chain?
- Which parts are deterministic and which parts are LLM-driven?
- Where is retrieval quality decided?
- Where is context assembled?

### Week 2

Focus: agent state and structured planner.

Questions to answer:

- Why must planner output be structured?
- Why does agent loop need max steps?
- What is an observation?
- What should be persisted?

### Week 3

Focus: tool use and MCP.

Questions to answer:

- What is the difference between MCP server, MCP client, and tool registry?
- How does a tool schema affect planner reliability?
- How should tool errors be converted into observations?

### Week 4

Focus: multi-step reasoning.

Questions to answer:

- When should the agent retrieve again?
- When should it ask clarification?
- How do we prevent hallucinated final answers?

### Week 5

Focus: ingestion and retrieval quality.

Questions to answer:

- Does chunking match user questions?
- Is metadata useful for filtering?
- Does rerank fix bad retrieval, or only reorder already-good candidates?

### Week 6

Focus: evaluation and safety.

Questions to answer:

- How do we know agentic mode is better?
- Which tool calls are risky?
- Which trace fields are needed for debugging?

### Week 7-8

Focus: polish, migration, and explanation.

Questions to answer:

- What is language-specific and what is architecture-specific?
- How would this look in LangGraph?
- How would this look in TypeScript?

## 7. Future Language Ecosystem Migration Plan

The goal is not to rewrite immediately. First extract architecture boundaries that survive language changes.

### 7.1 Language-Agnostic Contracts

Keep these concepts stable:

- `ChatModel`
- `EmbeddingModel`
- `RerankModel`
- `VectorStore`
- `Retriever`
- `ToolRegistry`
- `ToolExecutor`
- `AgentPlanner`
- `AgentState`
- `AgentLoopRunner`
- `TraceStore`
- `IngestionPipeline`

If these contracts are clear, the implementation language can change.

### 7.2 Python Migration

Best when the target is fast agent framework experimentation.

Candidate stack:

- FastAPI for API.
- LangGraph for agent loop/state graph.
- LangChain or LlamaIndex for retrievers/tools if useful.
- Pydantic for structured planner output.
- SQLAlchemy/Alembic for DB.
- Celery/RQ/Arq for background ingestion.
- Milvus/pgvector clients.

Migration route:

1. Keep Java backend running.
2. Build a Python agent service as a sidecar.
3. Expose Java retrieval/MCP functions as HTTP APIs or MCP tools.
4. Let Python LangGraph orchestrate first.
5. Gradually move retrieval/indexing only if needed.

Recommended first Python target:

```text
Python Agent Orchestrator
  calls Java RAG retrieval API
  calls Java MCP registry API
  writes trace back to Java or shared DB
```

Do not rewrite ingestion first. It is expensive and not needed for proving agent loop.

### 7.3 TypeScript Migration

Best when the target is full-stack product, Node ecosystem, or Vercel/Next.js deployment.

Candidate stack:

- NestJS or Hono/Fastify for backend.
- LangGraph.js for agent loop.
- Zod for planner/action schema validation.
- Prisma/Drizzle for DB.
- pgvector or Milvus SDK.
- OpenTelemetry for tracing.

Migration route:

1. Extract API contracts from Java.
2. Build a TypeScript agent orchestration service.
3. Reuse Java retrieval/MCP through HTTP/MCP.
4. Move frontend integration gradually.

Recommended first TypeScript target:

```text
TypeScript Agent Gateway
  validates action schemas with Zod
  calls existing Java services
  streams SSE to frontend
```

### 7.4 Keep Java When

Stay with Java/Spring if:

- You want enterprise backend credibility.
- You care about transactionality, existing DB mappings, and admin APIs.
- You need to explain production engineering.
- You do not need LangGraph-native features immediately.

### 7.5 Switch Or Add Sidecar When

Add Python/TypeScript sidecar if:

- You want faster agent loop experimentation.
- You need LangGraph state graph semantics.
- You want richer tool ecosystem integrations.
- You want to compare ecosystems for interviews or future work.

Preferred strategy:

```text
Do not big-bang rewrite.
First build sidecar orchestrator.
Then decide whether to migrate capabilities.
```

## 8. Daily Execution Template

Use this template each day:

```text
Day N:
Goal:
Files to inspect:
Files to change:
Expected behavior:
Verification:
Notes:
Next day:
```

Keep each day small. Prefer one useful commit-sized change over broad partial rewrites.

## 9. Suggested File/Package Layout For Agent Layer

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
  prompt/
    AgentPromptBuilder.java
  trace/
    AgentTraceSupport.java
  parser/
    AgentActionParser.java
```

Prompt resources:

```text
bootstrap/src/main/resources/prompt/agent-planner.st
bootstrap/src/main/resources/prompt/agent-final-answer.st
bootstrap/src/main/resources/prompt/agent-reflection.st
```

Tests:

```text
bootstrap/src/test/java/com/koawa/agent/agent/
  AgentActionParserTest.java
  AgentLoopRunnerTest.java
  AgentActionExecutorTest.java
```

## 10. Minimal MVP Sequence

The first implementation should be intentionally narrow:

1. User calls agent endpoint.
2. Load conversation history.
3. Planner emits `RETRIEVE_KB`.
4. Executor calls existing retrieval.
5. Observation stores retrieved context summary.
6. Planner emits `FINAL_ANSWER`.
7. Final answer streams to frontend.
8. Trace records both steps.

Only after this works, add MCP.

## 11. What Not To Do Early

Avoid these during the first 25 days:

- Do not rewrite `RetrievalEngine` from scratch.
- Do not replace all prompts at once.
- Do not migrate language ecosystem before Java MVP works.
- Do not add LangGraph sidecar before understanding the current pipeline.
- Do not start with multi-agent collaboration.
- Do not optimize UI before backend trace proves the loop.
- Do not make evaluation too large before the agent loop is stable.

## 12. Interview Explanation Frame

Use this frame to explain the project:

```text
The original system was an enterprise RAG platform with query rewrite,
intent routing, multi-channel vector retrieval, rerank, MCP tool calling,
ingestion pipeline, model routing, and trace.

The limitation was that orchestration was mostly static. I added an agent
loop above the existing capabilities. The agent maintains state, asks an LLM
planner for structured actions, executes retrieval or tools, observes results,
and decides whether to continue, clarify, or answer. Existing RAG and MCP
modules became tools rather than being rewritten.

This keeps production stability while adding agentic behavior.
```

## 13. Recovery Instructions For Future Sessions

When context is lost, do this:

1. Reread this file.
2. Inspect current git status.
3. Check which phase/day has files already created.
4. Continue from the next unfinished deliverable.
5. Keep changes narrow.
6. Before implementing, inspect the existing code path being touched.

Useful commands:

```powershell
git status --short
rg "AgentLoopRunner|AgentState|AgentAction" bootstrap/src/main/java
rg "agent-planner|agent-final-answer" bootstrap/src/main/resources/prompt
rg "agent" bootstrap/src/test/java
```

## 14. Current Priority

Immediate next step after writing this plan:

```text
Phase 1, Day 1:
Map the current chat flow from RAGChatController to StreamChatPipeline,
then write docs/current-rag-chain-notes.md.
```
