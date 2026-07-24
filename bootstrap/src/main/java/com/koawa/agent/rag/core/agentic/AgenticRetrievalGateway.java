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

import com.koawa.agent.rag.config.AgenticRetrievalProperties;
import com.koawa.agent.rag.dto.RetrievalContext;
import com.koawa.agent.rag.service.pipeline.StreamChatContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgenticRetrievalGateway {

    private final AgenticRetrievalRouteDecider routeDecider;
    private final AgenticRetrievalShadowService shadowService;
    private final AgenticRetrievalOrchestrator orchestrator;

    public RetrievalContext route(
            StreamChatContext chat,
            RetrievalContext singlePass,
            int topK) {
        AgenticRetrievalRouteDecision decision = routeDecider.decide(
                chat.getConversationId(), chat.getUserId(),
                chat.getRewriteResult(), chat.getSubIntents());
        log.info("Agentic Retrieval route: taskId={}, mode={}, reason={}, score={}, bucket={}",
                chat.getTaskId(), decision.mode(), decision.reason(),
                decision.complexity().score(), decision.bucket());
        if (decision.mode() == AgenticRetrievalProperties.Mode.SHADOW) {
            shadowService.submit(chat.getTaskId(), chat.getSubIntents(), singlePass, topK);
            return singlePass;
        }
        if (decision.mode() != AgenticRetrievalProperties.Mode.ACTIVE) {
            return singlePass;
        }
        try {
            AgenticRetrievalResult result = orchestrator.execute(
                    chat.getTaskId(), chat.getSubIntents(), singlePass, topK);
            if (isFailure(result.stopReason()) || result.retrievalContext() == null) {
                log.warn("Agentic Retrieval active fallback: taskId={}, reason={}",
                        chat.getTaskId(), result.stopReason());
                return singlePass;
            }
            return result.retrievalContext();
        } catch (RuntimeException exception) {
            log.warn("Agentic Retrieval active fallback after exception: taskId={}, reason={}",
                    chat.getTaskId(), exception.getMessage());
            return singlePass;
        }
    }

    private boolean isFailure(RetrievalStopReason reason) {
        return reason == RetrievalStopReason.CANCELLED
                || reason == RetrievalStopReason.TIMEOUT
                || reason == RetrievalStopReason.PLANNING_FAILED
                || reason == RetrievalStopReason.RETRIEVAL_FAILED
                || reason == RetrievalStopReason.EVALUATION_FAILED;
    }
}
