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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public final class AgentStreamChatAdapter {

    private final AgentChatService agentChatService;
    private final ConversationMemoryService memoryService;

    public boolean tryExecute(
            String question,
            String conversationId,
            String taskId,
            String userId,
            StreamCallback callback
    ) {
        AgentRunResult result;

        try {
            result = Objects.requireNonNull(
                    agentChatService.chat(
                            question,
                            conversationId,
                            taskId,
                            userId
                    ),
                    "agentChatService returned null result"
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Agent 执行异常，回退旧 RAG，conversationId={}，taskId={}",
                    conversationId,
                    taskId,
                    exception
            );
            return false;
        }

        if (!isDeliverable(result)) {
            log.warn(
                    "Agent 未产生可交付结果，回退旧 RAG，"
                            + "conversationId={}，taskId={}，stopReason={}",
                    conversationId,
                    taskId,
                    result.stopReason()
            );
            return false;
        }

        persistUserMessage(
                question,
                result.conversationId(),
                userId
        );

        callback.onContent(result.content());
        callback.onComplete();
        return true;
    }

    private void persistUserMessage(
            String question,
            String conversationId,
            String userId
    ) {
        try {
            memoryService.append(
                    conversationId,
                    userId,
                    ChatMessage.user(question)
            );
        } catch (RuntimeException exception) {
            // 消息持久化失败不能阻止已经生成的回答返回用户。
            log.warn(
                    "持久化 Agent 用户消息失败，conversationId={}",
                    conversationId,
                    exception
            );
        }
    }

    private boolean isDeliverable(AgentRunResult result) {
        boolean completed =
                result.stopReason() ==
                        AgentStopReason.FINAL_ANSWER
                || result.stopReason() ==
                        AgentStopReason.ASK_CLARIFICATION;

        return completed
                && result.content() != null
                && !result.content().isBlank();
    }
}
