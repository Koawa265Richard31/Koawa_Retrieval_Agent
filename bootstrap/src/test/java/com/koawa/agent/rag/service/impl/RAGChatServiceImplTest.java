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

package com.koawa.agent.rag.service.impl;

import com.koawa.agent.agent.routing.AgentRouteDecider;
import com.koawa.agent.agent.config.AgentRuntimeProperties;
import com.koawa.agent.agent.domain.AgentRunResult;
import com.koawa.agent.agent.domain.AgentStopReason;
import com.koawa.agent.agent.service.AgentChatService;
import com.koawa.agent.framework.context.LoginUser;
import com.koawa.agent.framework.context.UserContext;
import com.koawa.agent.infra.chat.StreamCallback;
import com.koawa.agent.rag.service.adapter.AgentStreamChatAdapter;
import com.koawa.agent.rag.core.memory.ConversationMemoryService;
import com.koawa.agent.rag.service.handler.StreamCallbackFactory;
import com.koawa.agent.rag.service.handler.StreamTaskManager;
import com.koawa.agent.rag.service.pipeline.StreamChatPipeline;
import com.koawa.agent.rag.service.ratelimit.ChatQueueLimiter;
import com.koawa.agent.rag.trace.StreamChatTraceRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RAGChatServiceImplTest {

    private final StreamChatPipeline chatPipeline =
            mock(StreamChatPipeline.class);
    private final ChatQueueLimiter chatQueueLimiter =
            mock(ChatQueueLimiter.class);
    private final StreamCallbackFactory callbackFactory =
            mock(StreamCallbackFactory.class);
    private final StreamChatTraceRunner traceRunner =
            mock(StreamChatTraceRunner.class);
    private final StreamTaskManager taskManager =
            mock(StreamTaskManager.class);
    private final AgentChatService agentChatService =
            mock(AgentChatService.class);
    private final ConversationMemoryService memoryService =
            mock(ConversationMemoryService.class);
    private final AgentRouteDecider routeDecider = routeDecider();
    private final AgentStreamChatAdapter agentAdapter =
            new AgentStreamChatAdapter(agentChatService, memoryService);
    private final StreamCallback callback = mock(StreamCallback.class);

    private final RAGChatServiceImpl service = new RAGChatServiceImpl(
            chatPipeline,
            chatQueueLimiter,
            callbackFactory,
            traceRunner,
            taskManager,
            routeDecider,
            agentAdapter
    );

    @BeforeEach
    void setUpUser() {
        UserContext.set(LoginUser.builder().userId("user-1").build());
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseSameTaskIdAcrossTraceAndAgentBranch() {
        SseEmitter emitter = new SseEmitter();
        when(callbackFactory.createChatEventHandler(
                eq(emitter),
                eq("conversation-1"),
                any(String.class)
        )).thenReturn(callback);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(3)).run();
            return null;
        }).when(chatQueueLimiter).enqueue(
                eq("question"),
                eq("conversation-1"),
                eq(emitter),
                any(Runnable.class)
        );
        doAnswer(invocation -> {
            Consumer<StreamCallback> businessLogic =
                    invocation.getArgument(4);
            businessLogic.accept(callback);
            return null;
        }).when(traceRunner).run(
                eq("question"),
                eq("conversation-1"),
                any(String.class),
                eq(callback),
                any(Consumer.class)
        );
        when(agentChatService.chat(
                eq("question"),
                eq("conversation-1"),
                any(String.class),
                eq("user-1")
        )).thenAnswer(invocation -> new AgentRunResult(
                "conversation-1",
                invocation.getArgument(2),
                AgentStopReason.FINAL_ANSWER,
                "answer",
                null
        ));

        service.streamChat(
                "question",
                " conversation-1 ",
                false,
                emitter
        );

        ArgumentCaptor<String> taskIdCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(callbackFactory).createChatEventHandler(
                eq(emitter),
                eq("conversation-1"),
                taskIdCaptor.capture()
        );

        String taskId = taskIdCaptor.getValue();
        assertFalse(taskId.isBlank());
        verify(traceRunner).run(
                eq("question"),
                eq("conversation-1"),
                eq(taskId),
                eq(callback),
                any(Consumer.class)
        );
        verify(agentChatService).chat(
                "question",
                "conversation-1",
                taskId,
                "user-1"
        );
        verifyNoInteractions(chatPipeline);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotFallBackToOldRagWhenAgentFailsAfterCancellation() {
        SseEmitter emitter = new SseEmitter();
        when(callbackFactory.createChatEventHandler(
                eq(emitter),
                eq("conversation-1"),
                any(String.class)
        )).thenReturn(callback);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(3)).run();
            return null;
        }).when(chatQueueLimiter).enqueue(
                eq("question"),
                eq("conversation-1"),
                eq(emitter),
                any(Runnable.class)
        );
        doAnswer(invocation -> {
            Consumer<StreamCallback> businessLogic =
                    invocation.getArgument(4);
            businessLogic.accept(callback);
            return null;
        }).when(traceRunner).run(
                eq("question"),
                eq("conversation-1"),
                any(String.class),
                eq(callback),
                any(Consumer.class)
        );
        when(agentChatService.chat(
                eq("question"),
                eq("conversation-1"),
                anyString(),
                eq("user-1")
        )).thenThrow(new IllegalStateException("planner failed"));
        when(taskManager.isCancelled(anyString())).thenReturn(true);

        service.streamChat(
                "question",
                "conversation-1",
                false,
                emitter
        );

        verify(callback).onComplete();
        verifyNoInteractions(chatPipeline);
    }

    private static AgentRouteDecider routeDecider() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setEnabled(true);
        properties.setRolloutPercentage(100);
        return new AgentRouteDecider(properties);
    }
}
