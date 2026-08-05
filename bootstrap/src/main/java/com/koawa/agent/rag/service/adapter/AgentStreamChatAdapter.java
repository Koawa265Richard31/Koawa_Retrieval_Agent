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
import java.util.concurrent.TimeUnit;

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
        long startedAtNanos = System.nanoTime();
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
            logRunSummary(
                    conversationId, taskId, null,
                    "EXCEPTION", true, true, startedAtNanos
            );
            return false;
        }

        if (result.stopReason() == AgentStopReason.CANCELLED) {
            callback.onComplete();
            logRunSummary(
                    conversationId, taskId, result,
                    "CANCELLED", false, false, startedAtNanos
            );
            return true;
        }

        if (shouldFallbackForClarification(result)) {
            log.warn(
                    "Agent 要求澄清，回退 RAG，"
                            + "conversationId={}，taskId={}",
                    conversationId,
                    taskId
            );
            logRunSummary(
                    conversationId, taskId, result,
                    "FALLBACK_REQUESTED", true, false, startedAtNanos
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
            logRunSummary(
                    conversationId, taskId, result,
                    "FALLBACK_REQUESTED", true, false, startedAtNanos
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
        logRunSummary(
                conversationId, taskId, result,
                "DELIVERED", false, false, startedAtNanos
        );
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
        return result.stopReason() == AgentStopReason.FINAL_ANSWER
                && result.content() != null
                && !result.content().isBlank();
    }

    private boolean shouldFallbackForClarification(AgentRunResult result) {
        return result.stopReason() == AgentStopReason.ASK_CLARIFICATION;
    }

    private void logRunSummary(
            String conversationId,
            String taskId,
            AgentRunResult result,
            String outcome,
            boolean fallbackRequested,
            boolean exceptionOccurred,
            long startedAtNanos
    ) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAtNanos
        );

        boolean hasContent = result != null
                && result.content() != null
                && !result.content().isBlank();

        boolean hasError = exceptionOccurred
                || result != null
                && result.errorMessage() != null
                && !result.errorMessage().isBlank();

        log.info(
                "agent_run_summary conversationId={}, taskId={}, outcome={}, "
                        + "stopReason={}, stepCount={}, "
                        + "planningRecoveryAttempts={}, failureType={}, "
                        + "elapsedMs={}, fallbackRequested={}, "
                        + "hasContent={}, hasError={}",
                conversationId,
                taskId,
                outcome,
                result == null ? null : result.stopReason(),
                result == null ? null : result.stepCount(),
                result == null
                        ? null
                        : result.planningRecoveryAttempts(),
                result == null ? null : result.failureType(),
                elapsedMs,
                fallbackRequested,
                hasContent,
                hasError
        );
    }
}
