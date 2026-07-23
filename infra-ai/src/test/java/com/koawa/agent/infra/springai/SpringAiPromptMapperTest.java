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
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpringAiPromptMapperTest {

    private final SpringAiPromptMapper mapper = new SpringAiPromptMapper();

    @Test
    void shouldPreserveMessageOrderAndRoles() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system("system"),
                        ChatMessage.user("question"),
                        ChatMessage.assistant("answer")
                ))
                .build();

        Prompt prompt = mapper.map(request);

        assertEquals(3, prompt.getInstructions().size());
        assertEquals(MessageType.SYSTEM, prompt.getInstructions().get(0).getMessageType());
        assertEquals("system", prompt.getInstructions().get(0).getText());
        assertEquals(MessageType.USER, prompt.getInstructions().get(1).getMessageType());
        assertEquals("question", prompt.getInstructions().get(1).getText());
        assertEquals(MessageType.ASSISTANT, prompt.getInstructions().get(2).getMessageType());
        assertEquals("answer", prompt.getInstructions().get(2).getText());
    }

    @Test
    void shouldMapPortableChatOptions() {
        ChatRequest request = ChatRequest.builder()
                .temperature(0.2)
                .topP(0.8)
                .topK(20)
                .maxTokens(512)
                .build();

        Prompt prompt = mapper.map(request);

        assertNotNull(prompt.getOptions());
        assertEquals(0.2, prompt.getOptions().getTemperature());
        assertEquals(0.8, prompt.getOptions().getTopP());
        assertEquals(20, prompt.getOptions().getTopK());
        assertEquals(512, prompt.getOptions().getMaxTokens());
    }

    @Test
    void shouldRejectInvalidMessagesAtTheBoundary() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(new ChatMessage(null, "content")))
                .build();

        NullPointerException error = assertThrows(NullPointerException.class, () -> mapper.map(request));

        assertEquals("message.role cannot be null", error.getMessage());
    }
}
