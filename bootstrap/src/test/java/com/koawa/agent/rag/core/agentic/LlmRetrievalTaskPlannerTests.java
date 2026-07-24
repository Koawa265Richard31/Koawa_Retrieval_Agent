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

import com.koawa.agent.framework.convention.ChatRequest;
import com.koawa.agent.infra.chat.LLMService;
import com.koawa.agent.rag.config.AgenticRetrievalProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmRetrievalTaskPlannerTests {

    @Test
    void noGapSkipsLlm() {
        LLMService llmService = mock(LLMService.class);
        RetrievalPlan result = planner(llmService).followUpPlan(
                initialPlan(),
                new EvidenceEvaluation(false, List.of(), List.of(), 0.5D, "no gaps"),
                RetrievalBudget.defaults());

        assertTrue(result.tasks().isEmpty());
        verify(llmService, never()).chat(any(ChatRequest.class));
    }

    @Test
    void validOutputUsesDeadlineAndPreservesTaskRouting() {
        LLMService llmService = mock(LLMService.class);
        when(llmService.chat(any(ChatRequest.class))).thenReturn(
                "{\"queries\":[{\"taskId\":\"task-1\",\"query\":\"focused\"}],"
                        + "\"rationale\":\"gap\"}");
        Instant before = Instant.now();

        RetrievalPlan result = planner(llmService).followUpPlan(
                initialPlan(), insufficient(), RetrievalBudget.defaults());

        assertEquals("focused", result.tasks().get(0).question());
        assertEquals(List.of("kb-1"), result.tasks().get(0).knowledgeBaseIds());
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).chat(captor.capture());
        assertTrue(captor.getValue().getDeadlineAt().isAfter(before));
    }

    @Test
    void invalidOutputIsRejected() {
        LLMService llmService = mock(LLMService.class);
        when(llmService.chat(any(ChatRequest.class))).thenReturn("invalid");

        assertThrows(
                IllegalArgumentException.class,
                () -> planner(llmService).followUpPlan(
                        initialPlan(), insufficient(), RetrievalBudget.defaults()));
    }

    private LlmRetrievalTaskPlanner planner(LLMService llmService) {
        return new LlmRetrievalTaskPlanner(
                llmService,
                new RetrievalPlanParser(),
                new RuleBasedRetrievalTaskPlanner(),
                new AgenticRetrievalProperties());
    }

    private RetrievalPlan initialPlan() {
        return new RetrievalPlan(
                List.of(new RetrievalTask(
                        "task-1", "question", List.of("kb-1"), Set.of("fact"), false)),
                "initial");
    }

    private EvidenceEvaluation insufficient() {
        return new EvidenceEvaluation(
                false,
                List.of(),
                List.of(new RetrievalGap("task-1", Set.of("fact"), "seed")),
                0.4D,
                "missing");
    }
}
