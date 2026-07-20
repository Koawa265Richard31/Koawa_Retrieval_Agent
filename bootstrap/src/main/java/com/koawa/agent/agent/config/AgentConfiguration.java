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

import com.koawa.agent.agent.event.AgentEventSink;
import com.koawa.agent.agent.executor.AgentActionExecutor;
import com.koawa.agent.agent.executor.AgentActionHandler;
import com.koawa.agent.agent.executor.RoutingAgentActionExecutor;
import com.koawa.agent.agent.executor.handler.AskClarificationActionHandler;
import com.koawa.agent.agent.executor.handler.CallMcpToolActionHandler;
import com.koawa.agent.agent.executor.handler.FinalAnswerActionHandler;
import com.koawa.agent.agent.executor.handler.RetrieveKbActionHandler;
import com.koawa.agent.agent.executor.policy.AgentExecutionPolicy;
import com.koawa.agent.agent.executor.policy.AllowListAgentExecutionPolicy;
import com.koawa.agent.agent.parser.AgentActionParser;
import com.koawa.agent.agent.planner.AgentPlanner;
import com.koawa.agent.agent.planner.AgentRequestAssembler;
import com.koawa.agent.agent.planner.LlmAgentPlanner;
import com.koawa.agent.agent.recovery.AgentRecoveryPolicy;
import com.koawa.agent.agent.recovery.DefaultAgentRecoveryPolicy;
import com.koawa.agent.agent.routing.AgentRouteDecider;
import com.koawa.agent.agent.runner.AgentCancellationChecker;
import com.koawa.agent.agent.runner.AgentLoopRunner;
import com.koawa.agent.agent.service.AgentChatService;
import com.koawa.agent.agent.service.AgentConversationHistoryLoader;
import com.koawa.agent.agent.service.impl.DefaultAgentChatService;
import com.koawa.agent.infra.chat.LLMService;
import com.koawa.agent.rag.config.SearchChannelProperties;
import com.koawa.agent.rag.core.intent.IntentResolver;
import com.koawa.agent.rag.core.mcp.McpToolRegistry;
import com.koawa.agent.rag.core.prompt.PromptTemplateLoader;
import com.koawa.agent.rag.core.retrieve.RetrievalEngine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

@Configuration
@EnableConfigurationProperties(AgentRuntimeProperties.class)
public class AgentConfiguration {
    @Bean
    public Clock agentClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RetrieveKbActionHandler retrieveKbActionHandler(
            IntentResolver intentResolver,
            RetrievalEngine retrievalEngine,
            SearchChannelProperties searchChannelProperties
    ) {
        return new RetrieveKbActionHandler(
                intentResolver,
                retrievalEngine,
                searchChannelProperties
        );
    }

    @Bean
    public AgentExecutionPolicy agentExecutionPolicy(
            AgentRuntimeProperties properties
    ) {
        return new AllowListAgentExecutionPolicy(
                properties.getAllowedToolIds()
        );
    }

    @Bean
    public CallMcpToolActionHandler callMcpToolActionHandler(
            McpToolRegistry mcpToolRegistry,
            AgentExecutionPolicy executionPolicy
    ) {
        return new CallMcpToolActionHandler(
                mcpToolRegistry,
                executionPolicy
        );
    }

    @Bean
    public AskClarificationActionHandler askClarificationActionHandler() {
        return new AskClarificationActionHandler();
    }

    @Bean
    public FinalAnswerActionHandler finalAnswerActionHandler(
            LLMService llmService,
            PromptTemplateLoader promptTemplateLoader
    ) {
        return new FinalAnswerActionHandler(
                llmService,
                promptTemplateLoader
        );
    }

    @Bean
    public AgentRequestAssembler agentRequestAssembler(
            PromptTemplateLoader promptTemplateLoader
    ) {
        return new AgentRequestAssembler(promptTemplateLoader);
    }

    @Bean
    public AgentPlanner agentPlanner(
            LLMService llmService,
            AgentActionParser actionParser,
            AgentRequestAssembler requestAssembler,
            McpToolRegistry mcpToolRegistry
    ) {
        return new LlmAgentPlanner(
                llmService,
                actionParser,
                requestAssembler,
                mcpToolRegistry
        );
    }

    @Bean
    public AgentActionExecutor agentActionExecutor(
            List<AgentActionHandler> handlers
    ) {
        return new RoutingAgentActionExecutor(handlers);
    }

    @Bean
    public AgentEventSink agentEventSink() {
        return AgentEventSink.NOOP;
    }

    @Bean
    public AgentRecoveryPolicy agentRecoveryPolicy() {
        return new DefaultAgentRecoveryPolicy();
    }

    @Bean
    public AgentLoopRunner agentLoopRunner(
            AgentPlanner planner,
            AgentActionExecutor executor,
            AgentEventSink eventSink,
            AgentCancellationChecker cancellationChecker,
            AgentRecoveryPolicy recoveryPolicy,
            Clock clock
    ) {
        return new AgentLoopRunner(
                planner,
                executor,
                eventSink,
                cancellationChecker,
                recoveryPolicy,
                clock
        );
    }

    @Bean
    public AgentChatService agentChatService(
            AgentLoopRunner runner,
            AgentRuntimeProperties properties,
            AgentConversationHistoryLoader historyLoader,
            Clock clock
    ) {
        return new DefaultAgentChatService(
                runner,
                properties,
                historyLoader,
                clock
        );
    }

    @Bean
    public AgentRouteDecider agentRouteDecider(
            AgentRuntimeProperties properties
    ) {
        return new AgentRouteDecider(properties);
    }
}
