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

package com.koawa.agent.infra.springai;

import com.koawa.agent.framework.convention.ChatRequest;
import com.koawa.agent.infra.http.ModelClientErrorType;
import com.koawa.agent.infra.http.ModelClientException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * Synchronous Spring AI invocation seam.
 *
 * <p>The class is intentionally not a Spring bean. Configuration and provider routing are added
 * only after this contract is stable.</p>
 */
public final class SpringAiSyncChatInvoker {

    private static final String EMPTY_RESPONSE_MESSAGE = "Spring AI 模型未返回有效内容";

    private final ChatModel chatModel;
    private final SpringAiPromptMapper promptMapper;

    public SpringAiSyncChatInvoker(ChatModel chatModel, SpringAiPromptMapper promptMapper) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel cannot be null");
        this.promptMapper = Objects.requireNonNull(promptMapper, "promptMapper cannot be null");
    }

    public String chat(ChatRequest request) {
        ChatResponse response = chatModel.call(promptMapper.map(request));
        if (response == null) {
            throw invalidResponse();
        }

        Generation result = response.getResult();
        AssistantMessage output = result == null ? null : result.getOutput();
        String content = output == null ? null : output.getText();
        if (!StringUtils.hasText(content)) {
            throw invalidResponse();
        }
        return content;
    }

    private ModelClientException invalidResponse() {
        return new ModelClientException(
                EMPTY_RESPONSE_MESSAGE,
                ModelClientErrorType.INVALID_RESPONSE,
                null
        );
    }
}
