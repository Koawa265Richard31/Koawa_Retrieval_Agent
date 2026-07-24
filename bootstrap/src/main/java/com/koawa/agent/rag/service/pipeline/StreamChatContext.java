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

package com.koawa.agent.rag.service.pipeline;

import com.koawa.agent.framework.convention.ChatMessage;
import com.koawa.agent.infra.chat.StreamCallback;
import com.koawa.agent.rag.core.rewrite.RewriteResult;
import com.koawa.agent.rag.dto.SubQuestionIntent;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 流式对话上下文
 */
@Getter
@Builder
public class StreamChatContext {

    // ==================== 不可变输入参数 ====================

    private final String question;
    private final String conversationId;
    private final String taskId;
    private final boolean deepThinking;
    private final String userId;
    private final String username;
    private final String userRole;
    private final StreamCallback callback;

    // ==================== 管道中填充的中间状态 ====================

    //是加载记忆后填的
    @Setter
    private List<ChatMessage> history;

    //是问题改写/拆分后填的。
    @Setter
    private RewriteResult rewriteResult;

    //是意图识别后填的。
    @Setter
    private List<SubQuestionIntent> subIntents;
}
