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

package com.koawa.agent.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.koawa.agent.agent.config.AgentRuntimeProperties;
import com.koawa.agent.agent.domain.AgentRunResult;
import com.koawa.agent.agent.domain.AgentState;
import com.koawa.agent.agent.runner.AgentLoopRunner;
import com.koawa.agent.agent.service.AgentChatService;

import java.util.Objects;

public final class DefaultAgentChatService implements AgentChatService {

    private final AgentLoopRunner runner;
    private final AgentRuntimeProperties properties;

    public DefaultAgentChatService(
            AgentLoopRunner runner,
            AgentRuntimeProperties properties
    ) {
        this.runner = Objects.requireNonNull(
                runner,
                "runner cannot be null"
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties cannot be null"
        );
    }

    @Override
    public AgentRunResult chat(
            String question,
            String conversationId,
            String taskId,
            String userId
    ) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException(
                    "question cannot be blank"
            );
        }

        String actualConversationId = resolveId(conversationId);
        String actualTaskId = resolveId(taskId);

        AgentState initialState = AgentState.builder()
                .conversationId(actualConversationId)
                .taskId(actualTaskId)
                .userId(userId)
                .originalQuestion(question)
                .currentStep(0)
                .maxSteps(properties.getMaxSteps())
                .build();

        AgentState completedState = Objects.requireNonNull(
                runner.run(initialState),
                "runner returned null state"
        );

        return AgentRunResult.from(completedState);
    }

    private String resolveId(String id) {
        if (id == null || id.isBlank()) {
            return IdUtil.getSnowflakeNextIdStr();
        }
        return id.trim();
    }
}
