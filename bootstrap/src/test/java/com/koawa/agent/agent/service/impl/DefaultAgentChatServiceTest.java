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

package com.koawa.agent.agent.service.impl;

import com.koawa.agent.agent.config.AgentRuntimeProperties;
import com.koawa.agent.agent.domain.AgentRunResult;
import com.koawa.agent.agent.domain.AgentState;
import com.koawa.agent.agent.domain.AgentStopReason;
import com.koawa.agent.agent.runner.AgentLoopRunner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAgentChatServiceTest {

    private final AgentLoopRunner runner = mock(AgentLoopRunner.class);
    private final AgentRuntimeProperties properties = runtimeProperties();
    private final DefaultAgentChatService service =
            new DefaultAgentChatService(runner, properties);

    @Test
    void shouldCreateStateAndReturnRunResult() {
        completeRunWithAnswer("answer");

        AgentRunResult result = service.chat(
                "question",
                " conversation-1 ",
                " task-1 ",
                "user-1"
        );

        ArgumentCaptor<AgentState> stateCaptor =
                ArgumentCaptor.forClass(AgentState.class);
        verify(runner).run(stateCaptor.capture());

        AgentState state = stateCaptor.getValue();
        assertEquals("conversation-1", state.getConversationId());
        assertEquals("task-1", state.getTaskId());
        assertEquals("user-1", state.getUserId());
        assertEquals("question", state.getOriginalQuestion());
        assertEquals(0, state.getCurrentStep());
        assertEquals(5, state.getMaxSteps());

        assertEquals("conversation-1", result.conversationId());
        assertEquals("task-1", result.taskId());
        assertEquals(AgentStopReason.FINAL_ANSWER, result.stopReason());
        assertEquals("answer", result.content());
    }

    @Test
    void shouldGenerateConversationAndTaskIdsWhenMissing() {
        completeRunWithAnswer("answer");

        AgentRunResult result = service.chat(
                "question",
                " ",
                null,
                "user-1"
        );

        assertFalse(result.conversationId().isBlank());
        assertFalse(result.taskId().isBlank());
    }

    private void completeRunWithAnswer(String answer) {
        when(runner.run(any(AgentState.class))).thenAnswer(invocation -> {
            AgentState state = invocation.getArgument(0);
            state.setStopReason(AgentStopReason.FINAL_ANSWER);
            state.setFinalAnswer(answer);
            return state;
        });
    }

    private static AgentRuntimeProperties runtimeProperties() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setMaxSteps(5);
        return properties;
    }
}
