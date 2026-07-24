/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.koawa.agent.rag.core.agentic;

import com.koawa.agent.rag.config.AgenticRetrievalProperties;
import com.koawa.agent.infra.http.ModelClientErrorType;
import com.koawa.agent.infra.http.ModelClientException;
import com.koawa.agent.rag.dto.RetrievalContext;
import com.koawa.agent.rag.dto.SubQuestionIntent;
import com.koawa.agent.rag.service.handler.StreamTaskManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAgenticRetrievalOrchestratorTests {

    private final RetrievalContextEvidenceAdapter adapter =
            mock(RetrievalContextEvidenceAdapter.class);
    private final LlmEvidenceEvaluator evaluator = mock(LlmEvidenceEvaluator.class);
    private final LlmRetrievalTaskPlanner planner = mock(LlmRetrievalTaskPlanner.class);
    private final AgenticRetrievalIterationExecutor retrievalExecutor =
            mock(AgenticRetrievalIterationExecutor.class);
    private final StreamTaskManager taskManager = mock(StreamTaskManager.class);
    private final AgenticRetrievalProperties properties = new AgenticRetrievalProperties();
    private final RetrievalPlanFactory planFactory = new RetrievalPlanFactory();
    private DefaultAgenticRetrievalOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new DefaultAgenticRetrievalOrchestrator(
                planFactory,
                adapter,
                evaluator,
                planner,
                retrievalExecutor,
                taskManager,
                properties);
    }

    @Test
    void sufficientFirstRoundDoesNotRetrieveAgain() throws Exception {
        when(adapter.adapt(any(), any(), anyInt())).thenReturn(List.of(evidence("chunk-1", 1)));
        when(evaluator.evaluate(any(), any(), any())).thenReturn(sufficient());

        AgenticRetrievalResult result = execute(initialContext());

        assertEquals(RetrievalStopReason.SUFFICIENT, result.stopReason());
        assertEquals(1, result.iterationCount());
        assertEquals(
                TaskEvidenceStatus.SUPPORTED,
                result.evidenceLedger().taskStates().get("retrieval-task-1").status());
        verify(retrievalExecutor, never()).retrieve(any(), anyInt(), any(), any());
    }

    @Test
    void insufficientFirstRoundRunsExactlyOneFollowUpAndMergesEvidence() throws Exception {
        RetrievalContext second = RetrievalContext.builder()
                .kbContext("second")
                .intentChunks(java.util.Map.of())
                .build();
        when(adapter.adapt(any(), any(), anyInt()))
                .thenReturn(List.of(evidence("chunk-1", 1)))
                .thenReturn(List.of(evidence("chunk-2", 2)));
        when(evaluator.evaluate(any(), any(), any()))
                .thenReturn(insufficient())
                .thenReturn(sufficient());
        when(planner.followUpPlan(any(), any(), any(), any()))
                .thenReturn(followUp("focused query"));
        when(retrievalExecutor.retrieve(any(), anyInt(), any(), any())).thenReturn(second);

        AgenticRetrievalResult result = execute(initialContext());

        assertEquals(RetrievalStopReason.SUFFICIENT, result.stopReason());
        assertEquals(2, result.iterationCount());
        assertEquals(2, result.evidenceLedger().evidence().size());
        assertEquals("first\nsecond", result.retrievalContext().getKbContext());
        verify(retrievalExecutor).retrieve(any(), anyInt(), any(), any());
    }

    @Test
    void noNewEvidenceStopsAfterSecondRound() throws Exception {
        when(adapter.adapt(any(), any(), anyInt()))
                .thenReturn(List.of(evidence("chunk-1", 1)))
                .thenReturn(List.of(evidence("chunk-1", 2)));
        when(evaluator.evaluate(any(), any(), any())).thenReturn(insufficient());
        when(planner.followUpPlan(any(), any(), any(), any()))
                .thenReturn(followUp("focused query"));
        when(retrievalExecutor.retrieve(any(), anyInt(), any(), any()))
                .thenReturn(initialContext());

        AgenticRetrievalResult result = execute(initialContext());

        assertEquals(RetrievalStopReason.NO_NEW_EVIDENCE, result.stopReason());
        assertEquals(2, result.iterationCount());
    }

    @Test
    void duplicateQueryStopsBeforeRetrieval() throws Exception {
        when(adapter.adapt(any(), any(), anyInt())).thenReturn(List.of(evidence("chunk-1", 1)));
        when(evaluator.evaluate(any(), any(), any())).thenReturn(insufficient());
        when(planner.followUpPlan(any(), any(), any(), any()))
                .thenReturn(followUp("question"));

        AgenticRetrievalResult result = execute(initialContext());

        assertEquals(RetrievalStopReason.DUPLICATE_QUERY, result.stopReason());
        verify(retrievalExecutor, never()).retrieve(any(), anyInt(), any(), any());
    }

    @Test
    void chunkBudgetStopsBeforeRetrieval() throws Exception {
        properties.setMaxRetrievedChunks(1);
        when(adapter.adapt(any(), any(), anyInt())).thenReturn(List.of(evidence("chunk-1", 1)));
        when(evaluator.evaluate(any(), any(), any())).thenReturn(insufficient());

        AgenticRetrievalResult result = execute(initialContext());

        assertEquals(RetrievalStopReason.BUDGET_EXHAUSTED, result.stopReason());
        verify(planner, never()).followUpPlan(any(), any(), any(), any());
    }

    @Test
    void cancellationStopsBeforePlanning() throws Exception {
        when(adapter.adapt(any(), any(), anyInt())).thenReturn(List.of(evidence("chunk-1", 1)));
        when(evaluator.evaluate(any(), any(), any())).thenReturn(insufficient());
        when(taskManager.isCancelled("stream-task")).thenReturn(true);

        AgenticRetrievalResult result = execute(initialContext());

        assertEquals(RetrievalStopReason.CANCELLED, result.stopReason());
        verify(planner, never()).followUpPlan(any(), any(), any(), any());
    }

    @Test
    void secondRoundFailureReturnsFirstContext() throws Exception {
        RetrievalContext initial = initialContext();
        when(adapter.adapt(any(), any(), anyInt())).thenReturn(List.of(evidence("chunk-1", 1)));
        when(evaluator.evaluate(any(), any(), any())).thenReturn(insufficient());
        when(planner.followUpPlan(any(), any(), any(), any()))
                .thenReturn(followUp("focused query"));
        when(retrievalExecutor.retrieve(any(), anyInt(), any(), any()))
                .thenThrow(new java.util.concurrent.ExecutionException(
                        new RuntimeException("down")));

        AgenticRetrievalResult result = execute(initial);

        assertEquals(RetrievalStopReason.RETRIEVAL_FAILED, result.stopReason());
        assertSame(initial, result.retrievalContext());
    }

    @Test
    void totalTimeoutReturnsExistingEvidence() throws Exception {
        properties.setTimeout(Duration.ofSeconds(1));
        when(adapter.adapt(any(), any(), anyInt())).thenReturn(List.of(evidence("chunk-1", 1)));
        when(evaluator.evaluate(any(), any(), any())).thenReturn(insufficient());
        when(planner.followUpPlan(any(), any(), any(), any()))
                .thenReturn(followUp("focused query"));
        when(retrievalExecutor.retrieve(any(), anyInt(), any(), any()))
                .thenThrow(new java.util.concurrent.TimeoutException("deadline"));

        AgenticRetrievalResult result = execute(initialContext());

        assertEquals(RetrievalStopReason.TIMEOUT, result.stopReason());
        assertTrue(result.evidenceLedger().evidence().size() > 0);
        verify(retrievalExecutor).retrieve(any(), anyInt(), any(), any());
    }

    @Test
    void evaluatorDeadlineIsClassifiedAsTimeout() {
        when(adapter.adapt(any(), any(), anyInt())).thenReturn(List.of(evidence("chunk-1", 1)));
        when(evaluator.evaluate(any(), any(), any())).thenThrow(
                new ModelClientException(
                        "deadline",
                        ModelClientErrorType.DEADLINE_EXCEEDED,
                        null));

        AgenticRetrievalResult result = execute(initialContext());

        assertEquals(RetrievalStopReason.TIMEOUT, result.stopReason());
    }

    private AgenticRetrievalResult execute(RetrievalContext context) {
        return orchestrator.execute(
                "stream-task",
                List.of(new SubQuestionIntent("question", List.of())),
                context,
                5);
    }

    private RetrievalContext initialContext() {
        return RetrievalContext.builder()
                .kbContext("first")
                .intentChunks(java.util.Map.of())
                .build();
    }

    private EvidenceItem evidence(String chunkId, int iteration) {
        return new EvidenceItem(
                "retrieval-task-1",
                chunkId,
                "doc-" + chunkId,
                "kb-1",
                "content",
                0.9D,
                "source",
                null,
                iteration);
    }

    private EvidenceEvaluation sufficient() {
        return new EvidenceEvaluation(
                true,
                List.of(new TaskAssessment(
                        "retrieval-task-1",
                        TaskEvidenceStatus.SUPPORTED,
                        Set.of("fact"),
                        Set.of(),
                        "enough")),
                List.of(),
                0.9D,
                "enough");
    }

    private EvidenceEvaluation insufficient() {
        return new EvidenceEvaluation(
                false,
                List.of(new TaskAssessment(
                        "retrieval-task-1",
                        TaskEvidenceStatus.PARTIALLY_SUPPORTED,
                        Set.of(),
                        Set.of("fact"),
                        "missing")),
                List.of(new RetrievalGap(
                        "retrieval-task-1", Set.of("fact"), "focused query")),
                0.4D,
                "missing");
    }

    private RetrievalPlan followUp(String query) {
        return new RetrievalPlan(
                List.of(new RetrievalTask(
                        "retrieval-task-1", query, List.of(), Set.of("fact"), false)),
                "follow-up");
    }
}
