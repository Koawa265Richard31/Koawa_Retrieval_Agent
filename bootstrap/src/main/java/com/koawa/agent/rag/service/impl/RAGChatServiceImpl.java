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

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.koawa.agent.agent.routing.AgentRouteDecider;
import com.koawa.agent.framework.context.UserContext;
import com.koawa.agent.infra.chat.StreamCallback;
import com.koawa.agent.rag.service.ChatExecutionMode;
import com.koawa.agent.rag.service.RAGChatService;
import com.koawa.agent.rag.service.adapter.AgentStreamChatAdapter;
import com.koawa.agent.rag.service.handler.StreamCallbackFactory;
import com.koawa.agent.rag.service.handler.StreamTaskManager;
import com.koawa.agent.rag.service.pipeline.StreamChatContext;
import com.koawa.agent.rag.service.pipeline.StreamChatPipeline;
import com.koawa.agent.rag.service.ratelimit.ChatQueueLimiter;
import com.koawa.agent.rag.trace.StreamChatTraceRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 对话服务默认实现
 * RAGChatServiceImpl 不负责具体 RAG 推理。
 * 它负责启动一次聊天任务：创建 conversationId/taskId，创建 SSE callback，
 * 经过限流和 trace 包装，再按灰度规则交给 Agent 或原 RAG Pipeline。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGChatServiceImpl implements RAGChatService {

    private final StreamChatPipeline chatPipeline;
    private final ChatQueueLimiter chatQueueLimiter;
    private final StreamCallbackFactory callbackFactory;
    private final StreamChatTraceRunner traceRunner;
    private final StreamTaskManager taskManager;
    private final AgentRouteDecider agentRouteDecider;
    private final AgentStreamChatAdapter agentStreamChatAdapter;

    @Override
    public void streamChat(
            String question,
            String conversationId,
            Boolean deepThinking,
            ChatExecutionMode executionMode,
            SseEmitter emitter
    ) {
        // 生成会话 ID 和任务 ID
        String actualConversationId =
                StrUtil.isBlank(conversationId)
                ? IdUtil.getSnowflakeNextIdStr()
                : conversationId.trim();

        String taskId = IdUtil.getSnowflakeNextIdStr();
        String userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        String userRole = UserContext.getRole();

        //这里不是直接往前端写内容，而是创建一个 StreamCallback。后面 LLM 每吐出一段内容，都会通过这个 callback 推给前端。
        StreamCallback callback =
                callbackFactory.createChatEventHandler(
                        emitter,
                        actualConversationId,
                        taskId
                );

        chatQueueLimiter.enqueue(
                question,
                actualConversationId,
                emitter,
                () -> traceRunner.run(
                        question,
                        actualConversationId,
                        taskId,
                        callback,
                        traceAware -> executeRoutedChat(
                                question,
                                actualConversationId,
                                taskId,
                                deepThinking,
                                executionMode,
                                userId,
                                username,
                                userRole,
                                traceAware
                        )
                )
        );
    }

    private void executeRoutedChat(
            String question,
            String conversationId,
            String taskId,
            Boolean deepThinking,
            ChatExecutionMode executionMode,
            String userId,
            String username,
            String userRole,
            StreamCallback callback
    ) {
        ChatExecutionMode actualMode = executionMode == null
                ? ChatExecutionMode.AUTO : executionMode;
        boolean useAgent = actualMode == ChatExecutionMode.AGENT
                || (actualMode == ChatExecutionMode.AUTO
                && agentRouteDecider.shouldUseAgent(conversationId, userId));

        if (useAgent && agentStreamChatAdapter.tryExecute(
                question,
                conversationId,
                taskId,
                userId,
                callback
        )) {
            return;
        }

        // Agent 失败本可回退旧 RAG，但用户取消拥有更高优先级，不能在取消后重新启动一条链路。
        if (taskManager.isCancelled(taskId)) {
            callback.onComplete();
            return;
        }

        StreamChatContext context = StreamChatContext.builder()
                .question(question)
                .conversationId(conversationId)
                .taskId(taskId)
                .deepThinking(Boolean.TRUE.equals(deepThinking))
                .executionMode(actualMode)
                .userId(userId)
                .username(username)
                .userRole(userRole)
                .callback(callback)
                .build();

        chatPipeline.execute(context);

    }

    @Override
    public void stopTask(String taskId) {
        taskManager.cancel(taskId);
    }
}
