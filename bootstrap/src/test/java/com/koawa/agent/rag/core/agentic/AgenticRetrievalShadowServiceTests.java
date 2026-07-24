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

import com.koawa.agent.framework.convention.RetrievedChunk;
import com.koawa.agent.rag.config.AgenticRetrievalProperties;
import com.koawa.agent.rag.dto.RetrievalContext;
import com.koawa.agent.rag.dto.SubQuestionIntent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgenticRetrievalShadowServiceTests {

    @Test
    void disabledShadowDoesNoWork() {
        AgenticRetrievalShadowRunner runner = mock(AgenticRetrievalShadowRunner.class);
        AgenticRetrievalProperties properties = new AgenticRetrievalProperties();

        new AgenticRetrievalShadowService(
                properties, runner, Runnable::run)
                .submit(List.of(), RetrievalContext.builder().build());

        verify(runner, never()).evaluate(any(), any());
    }

    @Test
    void enabledShadowEvaluatesWithoutReturningAReplacementContext() {
        AgenticRetrievalShadowRunner runner = mock(AgenticRetrievalShadowRunner.class);
        AgenticRetrievalProperties properties = new AgenticRetrievalProperties();
        properties.setShadowEnabled(true);
        Executor directExecutor = Runnable::run;
        RetrievalContext context = RetrievalContext.builder()
                .intentChunks(Map.of(
                        "intent",
                        List.of(RetrievedChunk.builder().id("chunk-1").build())))
                .build();
        new AgenticRetrievalShadowService(
                properties, runner, directExecutor)
                .submit(List.of(new SubQuestionIntent("question", List.of())), context);

        verify(runner).evaluate(any(), any());
    }

    @Test
    void evaluatorFailureIsContainedInsideShadow() {
        RetrievalContextEvidenceAdapter adapter = mock(RetrievalContextEvidenceAdapter.class);
        LlmEvidenceEvaluator evaluator = mock(LlmEvidenceEvaluator.class);
        AgenticRetrievalShadowRunner runner = new AgenticRetrievalShadowRunner(adapter, evaluator);
        AgenticRetrievalProperties properties = new AgenticRetrievalProperties();
        properties.setShadowEnabled(true);
        RetrievalContext context = RetrievalContext.builder()
                .intentChunks(Map.of("intent", List.of()))
                .build();
        when(adapter.adapt(any(), any(), anyInt())).thenReturn(List.of());
        when(evaluator.evaluate(any(), any())).thenThrow(new RuntimeException("model down"));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> new AgenticRetrievalShadowService(
                        properties, runner, Runnable::run)
                        .submit(List.of(new SubQuestionIntent("question", List.of())), context));
    }
}
