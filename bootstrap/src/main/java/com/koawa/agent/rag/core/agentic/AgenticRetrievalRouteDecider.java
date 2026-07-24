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
import com.koawa.agent.rag.core.rewrite.RewriteResult;
import com.koawa.agent.rag.dto.SubQuestionIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AgenticRetrievalRouteDecider {

    private final AgenticRetrievalProperties properties;
    private final RetrievalComplexityDecider complexityDecider;

    public AgenticRetrievalRouteDecision decide(
            String conversationId,
            String userId,
            RewriteResult rewriteResult,
            List<SubQuestionIntent> subIntents) {
        RetrievalComplexityDecision complexity =
                complexityDecider.decide(rewriteResult, subIntents);
        AgenticRetrievalProperties.Mode configured = properties.effectiveMode();
        if (configured == AgenticRetrievalProperties.Mode.OFF) {
            return off(complexity, -1, "mode_off");
        }
        if (!complexity.complex()) {
            return off(complexity, -1, "simple_query");
        }
        String key = hasText(conversationId) ? conversationId.trim()
                : hasText(userId) ? userId.trim() : null;
        if (key == null) {
            return off(complexity, -1, "missing_rollout_key");
        }
        int bucket = Math.floorMod(key.hashCode(), 100);
        int rollout = Math.max(0, Math.min(100, properties.getRolloutPercentage()));
        if (bucket >= rollout) {
            return off(complexity, bucket, "outside_rollout");
        }
        return new AgenticRetrievalRouteDecision(
                configured, complexity, bucket, "selected");
    }

    private AgenticRetrievalRouteDecision off(
            RetrievalComplexityDecision complexity, int bucket, String reason) {
        return new AgenticRetrievalRouteDecision(
                AgenticRetrievalProperties.Mode.OFF, complexity, bucket, reason);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
