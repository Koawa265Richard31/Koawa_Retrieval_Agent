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

package com.koawa.agent.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.koawa.agent.framework.convention.Result;
import com.koawa.agent.framework.exception.ClientException;
import com.koawa.agent.framework.idempotent.IdempotentSubmit;
import com.koawa.agent.framework.web.Results;
import com.koawa.agent.rag.config.RAGDefaultProperties;
import com.koawa.agent.rag.service.ChatExecutionMode;
import com.koawa.agent.rag.service.RAGChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 对话控制器
 * 提供流式问答与任务取消接口
 */
@RestController
@RequiredArgsConstructor
public class RAGChatController {

    private static final int MAX_CONVERSATION_ID_LENGTH = 20;

    private final RAGChatService ragChatService;
    private final RAGDefaultProperties ragDefaultProperties;

    /**
     * 发起 SSE 流式对话
     */
    @IdempotentSubmit(
            key = "T(com.koawa.agent.framework.context.UserContext).getUserId()",
            message = "当前会话处理中，请稍后再发起新的对话"
    )
    @GetMapping(value = "/rag/v3/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestParam String question,
                           @RequestParam(required = false) String conversationId,
                           @RequestParam(required = false, defaultValue = "false") Boolean deepThinking,
                           @RequestParam(required = false, defaultValue = "RAG") String executionMode,
                           @RequestParam(required = false) String collectionName) {
        validateConversationId(conversationId);
        validateCollectionName(collectionName);
        ChatExecutionMode resolvedMode = ChatExecutionMode.from(executionMode);
        if (resolvedMode == ChatExecutionMode.AGENT) {
            StpUtil.checkRole("admin");
        }
        SseEmitter emitter = new SseEmitter(ragDefaultProperties.getSseTimeoutMs());
        ragChatService.streamChat(
                question, conversationId, deepThinking, resolvedMode, collectionName, emitter);
        return emitter;
    }

    private void validateConversationId(String conversationId) {
        if (StrUtil.isBlank(conversationId)) {
            return;
        }
        if (conversationId.trim().length() > MAX_CONVERSATION_ID_LENGTH) {
            throw new ClientException("conversationId 长度不能超过 20 个字符");
        }
    }

    private void validateCollectionName(String collectionName) {
        if (StrUtil.isBlank(collectionName)) {
            return;
        }
        if (collectionName.trim().length() > 128) {
            throw new ClientException("collectionName 长度不能超过 128 个字符");
        }
    }

    /**
     * 停止指定任务
     */
    @IdempotentSubmit
    @PostMapping(value = "/rag/v3/stop")
    public Result<Void> stop(@RequestParam String taskId) {
        ragChatService.stopTask(taskId);
        return Results.success();
    }
}
