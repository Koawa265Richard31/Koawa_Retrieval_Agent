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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmEvidenceEvaluatorTests {

    @Test
    void emptyEvidenceDoesNotCallLlm() {
        LLMService llmService = mock(LLMService.class);
        RetrievalTask task = task();
        LlmEvidenceEvaluator evaluator = evaluator(llmService);

        EvidenceEvaluation result = evaluator.evaluate(
                new RetrievalPlan(List.of(task), "test"),
                EvidenceLedger.empty(List.of(task)));

        assertFalse(result.sufficient());
        verify(llmService, never()).chat(any(ChatRequest.class));
    }

    @Test
    void validEvidenceIsEvaluatedThroughStrictParser() {
        LLMService llmService = mock(LLMService.class);
        when(llmService.chat(any(ChatRequest.class))).thenReturn("""
                {"sufficient":false,"assessments":[{
                  "taskId":"task-1","status":"PARTIALLY_SUPPORTED",
                  "coveredFacts":[],"missingFacts":["fact"],"explanation":"missing"
                }],"gaps":[{
                  "taskId":"task-1","missingFacts":["fact"],"suggestedQuery":"query"
                }],"confidence":0.7,"explanation":"incomplete"}
                """);
        RetrievalTask task = task();
        EvidenceLedger ledger = EvidenceLedger.empty(List.of(task)).merge(
                List.of(new EvidenceItem(
                        "task-1", "chunk-1", "doc-1", "kb-1",
                        "content", 1, "source", null, 1)),
                null);

        EvidenceEvaluation result = evaluator(llmService).evaluate(
                new RetrievalPlan(List.of(task), "test"), ledger);

        assertFalse(result.sufficient());
        verify(llmService).chat(any(ChatRequest.class));
    }

    @Test
    void invalidModelOutputIsNotSilentlyAccepted() {
        LLMService llmService = mock(LLMService.class);
        when(llmService.chat(any(ChatRequest.class))).thenReturn("not-json");
        RetrievalTask task = task();
        EvidenceLedger ledger = EvidenceLedger.empty(List.of(task)).merge(
                List.of(new EvidenceItem(
                        "task-1", "chunk-1", "doc-1", "kb-1",
                        "content", 1, "source", null, 1)),
                null);

        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator(llmService).evaluate(
                        new RetrievalPlan(List.of(task), "test"), ledger));
    }

    private LlmEvidenceEvaluator evaluator(LLMService llmService) {
        return new LlmEvidenceEvaluator(
                llmService,
                new DeterministicEvidenceChecks(),
                new EvidenceEvaluationParser(),
                new AgenticRetrievalProperties());
    }

    private RetrievalTask task() {
        return new RetrievalTask(
                "task-1", "question", List.of(), Set.of("fact"), false);
    }
}
