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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgenticRetrievalRouteDeciderTests {

    @Test
    void simpleQueryNeverEntersAgenticMode() {
        AgenticRetrievalRouteDecision decision = decider(properties(
                AgenticRetrievalProperties.Mode.ACTIVE, 100)).decide(
                "conversation", "user",
                new RewriteResult("simple", List.of("simple")),
                List.of(new SubQuestionIntent("simple", List.of())));

        assertEquals(AgenticRetrievalProperties.Mode.OFF, decision.mode());
        assertEquals("simple_query", decision.reason());
    }

    @Test
    void sameConversationHasStableRouteRegardlessOfUser() {
        AgenticRetrievalRouteDecider decider = decider(properties(
                AgenticRetrievalProperties.Mode.SHADOW, 50));

        AgenticRetrievalRouteDecision first =
                decider.decide("conversation", "user-a", complex(), intents());
        AgenticRetrievalRouteDecision second =
                decider.decide("conversation", "user-b", complex(), intents());

        assertEquals(first.bucket(), second.bucket());
        assertEquals(first.mode(), second.mode());
    }

    @Test
    void legacyShadowSwitchMapsToShadowMode() {
        AgenticRetrievalProperties properties = properties(
                AgenticRetrievalProperties.Mode.OFF, 100);
        properties.setShadowEnabled(true);

        assertEquals(AgenticRetrievalProperties.Mode.SHADOW,
                decider(properties).decide(
                        "conversation", null, complex(), intents()).mode());
    }

    private AgenticRetrievalRouteDecider decider(
            AgenticRetrievalProperties properties) {
        return new AgenticRetrievalRouteDecider(
                properties, new RuleBasedRetrievalComplexityDecider());
    }

    private AgenticRetrievalProperties properties(
            AgenticRetrievalProperties.Mode mode, int rollout) {
        AgenticRetrievalProperties properties = new AgenticRetrievalProperties();
        properties.setMode(mode);
        properties.setRolloutPercentage(rollout);
        return properties;
    }

    private RewriteResult complex() {
        return new RewriteResult("complex", List.of("one", "two"));
    }

    private List<SubQuestionIntent> intents() {
        return List.of(
                new SubQuestionIntent("one", List.of()),
                new SubQuestionIntent("two", List.of()));
    }
}
