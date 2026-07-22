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

package com.koawa.agent.rag.service.adapter;

import com.koawa.agent.agent.domain.AgentRunResult;
import com.koawa.agent.agent.domain.AgentStopReason;
import com.koawa.agent.agent.service.AgentChatService;
import com.koawa.agent.framework.convention.ChatMessage;
import com.koawa.agent.infra.chat.StreamCallback;
import com.koawa.agent.rag.core.memory.ConversationMemoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class AgentStreamChatAdapterTest {

    private final AgentChatService agentChatService =
            mock(AgentChatService.class);
    private final ConversationMemoryService memoryService =
            mock(ConversationMemoryService.class);
    private final StreamCallback callback = mock(StreamCallback.class);
    private final AgentStreamChatAdapter adapter =
            new AgentStreamChatAdapter(agentChatService, memoryService);

    @Test
    void shouldDeliverAgentResultWithSameTaskId(CapturedOutput output) {
        when(agentChatService.chat(
                "question",
                "conversation-1",
                "task-1",
                "user-1"
        )).thenReturn(new AgentRunResult(
                "conversation-1",
                "task-1",
                AgentStopReason.FINAL_ANSWER,
                1,
                0,
                null,
                "sensitive-content",
                null
        ));

        boolean delivered = adapter.tryExecute(
                "question",
                "conversation-1",
                "task-1",
                "user-1",
                callback
        );

        assertTrue(delivered);
        verify(agentChatService).chat(
                "question",
                "conversation-1",
                "task-1",
                "user-1"
        );
        verify(memoryService).append(
                eq("conversation-1"),
                eq("user-1"),
                any(ChatMessage.class)
        );
        verify(callback).onContent("sensitive-content");
        verify(callback).onComplete();

        String logs = output.getOut();
        assertTrue(logs.contains("agent_run_summary"));
        assertTrue(logs.contains("outcome=DELIVERED"));
        assertTrue(logs.contains("stopReason=FINAL_ANSWER"));
        assertTrue(logs.contains("stepCount=1"));
        assertTrue(logs.contains("planningRecoveryAttempts=0"));
        assertTrue(logs.contains("fallbackRequested=false"));
        assertTrue(logs.contains("hasContent=true"));
        assertFalse(logs.contains("sensitive-content"));
    }

    @Test
    void shouldRequestOldRagFallbackForMaximumSteps() {
        when(agentChatService.chat(
                "question",
                "conversation-1",
                "task-1",
                "user-1"
        )).thenReturn(new AgentRunResult(
                "conversation-1",
                "task-1",
                AgentStopReason.MAX_STEPS,
                5,
                0,
                null,
                null,
                null
        ));

        boolean delivered = adapter.tryExecute(
                "question",
                "conversation-1",
                "task-1",
                "user-1",
                callback
        );

        assertFalse(delivered);
        verifyNoInteractions(memoryService, callback);
    }

    @Test
    void shouldRequestOldRagFallbackForTimeout(CapturedOutput output) {
        when(agentChatService.chat(
                "question",
                "conversation-1",
                "task-1",
                "user-1"
        )).thenReturn(new AgentRunResult(
                "conversation-1",
                "task-1",
                AgentStopReason.TIMEOUT,
                2,
                0,
                null,
                null,
                "sensitive-error"
        ));

        boolean delivered = adapter.tryExecute(
                "question",
                "conversation-1",
                "task-1",
                "user-1",
                callback
        );

        assertFalse(delivered);
        verifyNoInteractions(memoryService, callback);

        String logs = output.getOut();
        assertTrue(logs.contains("agent_run_summary"));
        assertTrue(logs.contains("outcome=FALLBACK_REQUESTED"));
        assertTrue(logs.contains("stopReason=TIMEOUT"));
        assertTrue(logs.contains("stepCount=2"));
        assertTrue(logs.contains("fallbackRequested=true"));
        assertTrue(logs.contains("hasError=true"));
        assertFalse(logs.contains("sensitive-error"));
    }

    @Test
    void shouldCompleteCancelledAgentWithoutFallingBackOrPersisting() {
        when(agentChatService.chat(
                "question",
                "conversation-1",
                "task-1",
                "user-1"
        )).thenReturn(new AgentRunResult(
                "conversation-1",
                "task-1",
                AgentStopReason.CANCELLED,
                0,
                0,
                null,
                null,
                null
        ));

        boolean handled = adapter.tryExecute(
                "question",
                "conversation-1",
                "task-1",
                "user-1",
                callback
        );

        assertTrue(handled);
        verify(callback).onComplete();
        verify(callback, never()).onContent(any());
        verifyNoInteractions(memoryService);
    }
}
