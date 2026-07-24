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
                .submit("task", List.of(), RetrievalContext.builder().build(), 5);

        verify(runner, never()).evaluate(any(), any(), any(), anyInt());
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
                .submit(
                        "task",
                        List.of(
                                new SubQuestionIntent("question-1", List.of()),
                                new SubQuestionIntent("question-2", List.of())),
                        context,
                        5);

        verify(runner).evaluate(any(), any(), any(), anyInt());
    }

    @Test
    void singleSubQuestionDoesNotAddModelCalls() {
        AgenticRetrievalShadowRunner runner = mock(AgenticRetrievalShadowRunner.class);
        AgenticRetrievalProperties properties = new AgenticRetrievalProperties();
        properties.setShadowEnabled(true);

        new AgenticRetrievalShadowService(properties, runner, Runnable::run)
                .submit(
                        "task",
                        List.of(new SubQuestionIntent("simple question", List.of())),
                        RetrievalContext.builder().build(),
                        5);

        verify(runner, never()).evaluate(any(), any(), any(), anyInt());
    }

    @Test
    void evaluatorFailureIsContainedInsideShadow() {
        AgenticRetrievalOrchestrator orchestrator = mock(AgenticRetrievalOrchestrator.class);
        AgenticRetrievalShadowRunner runner = new AgenticRetrievalShadowRunner(orchestrator);
        AgenticRetrievalProperties properties = new AgenticRetrievalProperties();
        properties.setShadowEnabled(true);
        RetrievalContext context = RetrievalContext.builder()
                .intentChunks(Map.of("intent", List.of()))
                .build();
        when(orchestrator.execute(any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("model down"));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> new AgenticRetrievalShadowService(
                        properties, runner, Runnable::run)
                        .submit(
                                "task",
                                List.of(
                                        new SubQuestionIntent("question-1", List.of()),
                                        new SubQuestionIntent("question-2", List.of())),
                                context,
                                5));
    }
}
