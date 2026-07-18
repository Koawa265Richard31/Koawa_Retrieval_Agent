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

package com.koawa.agent.agent.config;

import com.koawa.agent.agent.domain.AgentActionType;
import com.koawa.agent.agent.event.AgentEventSink;
import com.koawa.agent.agent.executor.AgentActionExecutor;
import com.koawa.agent.agent.executor.AgentActionHandler;
import com.koawa.agent.agent.executor.RoutingAgentActionExecutor;
import com.koawa.agent.agent.executor.handler.AskClarificationActionHandler;
import com.koawa.agent.agent.executor.handler.CallMcpToolActionHandler;
import com.koawa.agent.agent.executor.handler.FinalAnswerActionHandler;
import com.koawa.agent.agent.executor.handler.RetrieveKbActionHandler;
import com.koawa.agent.agent.executor.policy.AgentExecutionPolicy;
import com.koawa.agent.agent.parser.AgentActionParser;
import com.koawa.agent.agent.planner.AgentPlanner;
import com.koawa.agent.agent.planner.AgentRequestAssembler;
import com.koawa.agent.agent.planner.LlmAgentPlanner;
import com.koawa.agent.agent.runner.AgentLoopRunner;
import com.koawa.agent.infra.chat.LLMService;
import com.koawa.agent.rag.config.SearchChannelProperties;
import com.koawa.agent.rag.core.intent.IntentResolver;
import com.koawa.agent.rag.core.mcp.McpToolRegistry;
import com.koawa.agent.rag.core.prompt.PromptTemplateLoader;
import com.koawa.agent.rag.core.retrieve.RetrievalEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

public class AgentConfigurationTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(AgentConfiguration.class)
                    .withBean(
                            IntentResolver.class,
                            () -> mock(IntentResolver.class)
                    )
                    .withBean(
                            RetrievalEngine.class,
                            () -> mock(RetrievalEngine.class)
                    )
                    .withBean(
                            SearchChannelProperties.class,
                            () -> mock(SearchChannelProperties.class)
                    )
                    .withBean(
                            McpToolRegistry.class,
                            () -> mock(McpToolRegistry.class)
                    )
                    .withBean(
                            LLMService.class,
                            () -> mock(LLMService.class)
                    )
                    .withBean(
                            PromptTemplateLoader.class,
                            () -> mock(PromptTemplateLoader.class)
                    )
                    .withBean(
                            AgentActionParser.class,
                            () -> mock(AgentActionParser.class)
                    );

    @Test
    void shouldRegisterAgentRuntime() {
        contextRunner.run(context -> {
            assertThat(context)
                    .hasSingleBean(RetrieveKbActionHandler.class)
                    .hasSingleBean(CallMcpToolActionHandler.class)
                    .hasSingleBean(AskClarificationActionHandler.class)
                    .hasSingleBean(FinalAnswerActionHandler.class)
                    .hasSingleBean(AgentActionExecutor.class)
                    .hasSingleBean(AgentExecutionPolicy.class)
                    .hasSingleBean(AgentEventSink.class)
                    .hasSingleBean(AgentRequestAssembler.class)
                    .hasSingleBean(AgentPlanner.class)
                    .hasSingleBean(AgentLoopRunner.class);

            assertThat(context.getBean(AgentActionExecutor.class))
                    .isInstanceOf(RoutingAgentActionExecutor.class);
            assertThat(context.getBean(AgentPlanner.class))
                    .isInstanceOf(LlmAgentPlanner.class);

            assertThat(
                    context.getBeansOfType(AgentActionHandler.class).values()
            )
                    .extracting(AgentActionHandler::supportedAction)
                    .containsExactlyInAnyOrder(AgentActionType.values());
        });
    }
}
