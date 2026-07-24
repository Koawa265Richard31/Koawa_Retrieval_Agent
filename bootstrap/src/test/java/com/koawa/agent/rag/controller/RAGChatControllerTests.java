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

import cn.dev33.satoken.exception.SaTokenException;
import com.koawa.agent.rag.config.RAGDefaultProperties;
import com.koawa.agent.rag.service.ChatExecutionMode;
import com.koawa.agent.rag.service.RAGChatService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RAGChatControllerTests {

    @Test
    void autoModeDoesNotRequireAdminRole() {
        RAGChatService service = mock(RAGChatService.class);
        RAGDefaultProperties properties = new RAGDefaultProperties();
        RAGChatController controller = new RAGChatController(service, properties);

        controller.chat("question", null, false, "AUTO");

        verify(service).streamChat(
                eq("question"), eq(null), eq(false), eq(ChatExecutionMode.AUTO), any());
    }

    @Test
    void forcedModeRejectsCallerWithoutAdminRole() {
        RAGChatService service = mock(RAGChatService.class);
        RAGDefaultProperties properties = new RAGDefaultProperties();
        RAGChatController controller = new RAGChatController(service, properties);

        assertThrows(
                SaTokenException.class,
                () -> controller.chat("question", null, false, "AGENTIC"));
        verifyNoInteractions(service);
    }
}
