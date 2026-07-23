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

import com.koawa.agent.framework.convention.ChatMessage;
import com.koawa.agent.framework.convention.ChatRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Objects;

/**
 * Converts the project's stable chat contract to Spring AI model types.
 *
 * <p>This mapper deliberately has no Spring stereotype. It is the first migration seam and does
 * not change the active {@code LLMService} implementation.</p>
 */
public final class SpringAiPromptMapper {

    public Prompt map(ChatRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        List<ChatMessage> sourceMessages = Objects.requireNonNull(
                request.getMessages(),
                "request.messages cannot be null"
        );
        List<Message> messages = sourceMessages.stream()
                .map(this::mapMessage)
                .toList();

        ChatOptions options = ChatOptions.builder()
                .temperature(request.getTemperature())
                .topP(request.getTopP())
                .topK(request.getTopK())
                .maxTokens(request.getMaxTokens())
                .build();

        return new Prompt(messages, options);
    }

    private Message mapMessage(ChatMessage source) {
        Objects.requireNonNull(source, "request.messages cannot contain null");
        ChatMessage.Role role = Objects.requireNonNull(source.getRole(), "message.role cannot be null");
        String content = Objects.requireNonNull(source.getContent(), "message.content cannot be null");

        return switch (role) {
            case SYSTEM -> new SystemMessage(content);
            case USER -> new UserMessage(content);
            case ASSISTANT -> new AssistantMessage(content);
        };
    }
}
