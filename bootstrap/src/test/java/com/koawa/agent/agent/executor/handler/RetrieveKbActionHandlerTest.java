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

package com.koawa.agent.agent.executor.handler;

import com.koawa.agent.agent.domain.AgentAction;
import com.koawa.agent.agent.domain.AgentActionType;
import com.koawa.agent.agent.domain.AgentObservation;
import com.koawa.agent.agent.domain.AgentState;
import com.koawa.agent.rag.config.SearchChannelProperties;
import com.koawa.agent.rag.core.intent.IntentNode;
import com.koawa.agent.rag.core.intent.IntentResolver;
import com.koawa.agent.rag.core.intent.NodeScore;
import com.koawa.agent.rag.core.retrieve.RetrievalEngine;
import com.koawa.agent.rag.core.rewrite.RewriteResult;
import com.koawa.agent.rag.dto.RetrievalContext;
import com.koawa.agent.rag.dto.SubQuestionIntent;
import com.koawa.agent.rag.enums.IntentKind;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetrieveKbActionHandlerTest {

    @Test
    void shouldRetrieveOnlyKbIntentAndBuildObservation() {
        IntentResolver intentResolver = mock(IntentResolver.class);
        RetrievalEngine retrievalEngine = mock(RetrievalEngine.class);

        SearchChannelProperties searchChannelProperties = new SearchChannelProperties();

        RetrieveKbActionHandler handler = new RetrieveKbActionHandler(
                intentResolver,
                retrievalEngine,
                searchChannelProperties
        );

        NodeScore kbScore = NodeScore.builder()
                .node(IntentNode.builder()
                        .id("kb_hr")
                        .kind(IntentKind.KB)
                        .build())
                .score(0.9)
                .build();

        NodeScore mcpScore = NodeScore.builder()
                .node(IntentNode.builder()
                        .mcpToolId("mcp_sales")
                        .kind(IntentKind.MCP)
                        .build())
                .score(0.8)
                .build();

        when(intentResolver.resolve(any(RewriteResult.class)))
                .thenReturn(List.of(
                        new SubQuestionIntent(
                                "员工请假流程",
                                List.of(kbScore, mcpScore)
                        ))
                );

        when(retrievalEngine.retrieve(anyList(), eq(5)))
                .thenReturn(RetrievalContext.builder()
                        .kbContext("请假需要直属领导审批")
                        .intentChunks(Map.of())
                        .build()
                );

        AgentAction action = AgentAction.builder()
                .type(AgentActionType.RETRIEVE_KB)
                .arguments(Map.of(
                        "query", "员工请假流程",
                        "topK", 5
                ))
                .build();

        AgentObservation result = handler.execute(action, AgentState.builder().build());

        assertTrue(result.isSuccess());

        assertEquals(
                "请假需要直属领导审批",
                result.getContent()
        );
        assertEquals(5, result.getMetadata().get("topK"));
        assertEquals(false, result.getMetadata().get("empty"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubQuestionIntent>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(retrievalEngine).retrieve(captor.capture(), eq(5));

        List<NodeScore> passedScores =
                captor.getValue().get(0).nodeScores();

        assertEquals(1, passedScores.size());
        assertSame(kbScore, passedScores.get(0));
    }
}