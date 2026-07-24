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
import com.koawa.agent.rag.dto.RetrievalContext;
import com.koawa.agent.rag.service.pipeline.StreamChatContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgenticRetrievalGatewayTests {

    private final AgenticRetrievalRouteDecider routeDecider =
            mock(AgenticRetrievalRouteDecider.class);
    private final AgenticRetrievalShadowService shadowService =
            mock(AgenticRetrievalShadowService.class);
    private final AgenticRetrievalOrchestrator orchestrator =
            mock(AgenticRetrievalOrchestrator.class);
    private final AgenticRetrievalGateway gateway =
            new AgenticRetrievalGateway(
                    routeDecider, shadowService, orchestrator,
                    new EvidenceContextPresenter(new EvidenceCitationMapper()));

    @Test
    void shadowKeepsSinglePassAnswerContext() {
        RetrievalContext single = RetrievalContext.builder().kbContext("single").build();
        route(AgenticRetrievalProperties.Mode.SHADOW);

        RetrievalContext result = gateway.route(chat(), single, 5);

        assertSame(single, result);
        verify(shadowService).submit(any(), any(), any(), anyInt());
        verify(orchestrator, never()).execute(any(), any(), any(), anyInt(), any());
    }

    @Test
    void activeUsesAgenticContextOnNonFailureStop() {
        RetrievalContext single = RetrievalContext.builder().kbContext("single").build();
        RetrievalContext enhanced = RetrievalContext.builder().kbContext("enhanced").build();
        route(AgenticRetrievalProperties.Mode.ACTIVE);
        when(orchestrator.execute(any(), any(), any(), anyInt(), any()))
                .thenReturn(new AgenticRetrievalResult(
                        enhanced, null, RetrievalStopReason.SUFFICIENT, 2, true));

        assertSame(enhanced, gateway.route(chat(), single, 5));
    }

    @Test
    void activeFailureFallsBackToSinglePass() {
        RetrievalContext single = RetrievalContext.builder().kbContext("single").build();
        route(AgenticRetrievalProperties.Mode.ACTIVE);
        when(orchestrator.execute(any(), any(), any(), anyInt(), any()))
                .thenReturn(new AgenticRetrievalResult(
                        null, null, RetrievalStopReason.TIMEOUT, 1, false));

        assertSame(single, gateway.route(chat(), single, 5));
    }

    @Test
    void activeExceptionFallsBackToSinglePass() {
        RetrievalContext single = RetrievalContext.builder().kbContext("single").build();
        route(AgenticRetrievalProperties.Mode.ACTIVE);
        when(orchestrator.execute(any(), any(), any(), anyInt(), any()))
                .thenThrow(new RuntimeException("provider unavailable"));

        assertSame(single, gateway.route(chat(), single, 5));
    }

    private void route(AgenticRetrievalProperties.Mode mode) {
        when(routeDecider.decide(any(), any(), any(), any()))
                .thenReturn(new AgenticRetrievalRouteDecision(
                        mode,
                        new RetrievalComplexityDecision(true, 4, java.util.List.of()),
                        1,
                        "selected"));
    }

    private StreamChatContext chat() {
        return StreamChatContext.builder()
                .taskId("task")
                .conversationId("conversation")
                .userId("user")
                .build();
    }
}
