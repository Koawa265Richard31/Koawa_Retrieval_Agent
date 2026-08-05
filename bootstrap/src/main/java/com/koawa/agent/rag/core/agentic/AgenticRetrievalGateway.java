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

package com.koawa.agent.rag.core.agentic;

import com.koawa.agent.rag.dto.RetrievalContext;
import com.koawa.agent.rag.service.ChatExecutionMode;
import com.koawa.agent.rag.service.pipeline.StreamChatContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AgenticRetrievalGateway {

    public RetrievalContext route(
            StreamChatContext chat,
            RetrievalContext singlePass,
            int topK) {
        ChatExecutionMode executionMode = chat.getExecutionMode() == null
                ? ChatExecutionMode.RAG : chat.getExecutionMode();
        log.info("Agentic Retrieval bypassed by execution mode: taskId={}, mode={}",
                chat.getTaskId(), executionMode);
        return singlePass;
    }
}
