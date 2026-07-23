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
import com.koawa.agent.infra.http.ModelClientErrorType;
import com.koawa.agent.infra.http.ModelClientException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiSyncChatInvokerTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final SpringAiSyncChatInvoker invoker = new SpringAiSyncChatInvoker(
            chatModel,
            new SpringAiPromptMapper()
    );

    @Test
    void shouldCallChatModelAndReturnText() {
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage("answer"));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("question")))
                .build();

        String result = invoker.chat(request);

        assertEquals("answer", result);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void shouldClassifyNullResponseAsInvalidResponse() {
        when(chatModel.call(any(Prompt.class))).thenReturn(null);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("question")))
                .build();

        ModelClientException error = assertThrows(
                ModelClientException.class,
                () -> invoker.chat(request)
        );

        assertEquals(ModelClientErrorType.INVALID_RESPONSE, error.getErrorType());
        assertEquals("Spring AI 模型未返回有效内容", error.getMessage());
    }
}
