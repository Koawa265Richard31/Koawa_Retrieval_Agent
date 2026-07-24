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

import com.google.gson.Gson;
import com.koawa.agent.framework.convention.ChatMessage;
import com.koawa.agent.framework.convention.ChatRequest;
import com.koawa.agent.infra.chat.LLMService;
import com.koawa.agent.rag.config.AgenticRetrievalProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LlmRetrievalTaskPlanner implements RetrievalTaskPlanner {

    private static final String SYSTEM_PROMPT = """
            Generate only follow-up search queries for the supplied evidence gaps.
            Return exactly one JSON object: {"queries":[{"taskId":"...","query":"..."}],
            "rationale":"..."}. Use only known taskIds, at most the supplied maximum.
            Queries must be concise and independently searchable. Do not answer the question.
            """;

    private final LLMService llmService;
    private final RetrievalPlanParser parser;
    private final RuleBasedRetrievalTaskPlanner ruleBasedPlanner;
    private final AgenticRetrievalProperties properties;
    private final Gson gson = new Gson();

    @Override
    public RetrievalPlan followUpPlan(
            RetrievalPlan currentPlan,
            EvidenceEvaluation evaluation,
            RetrievalBudget budget) {
        return followUpPlan(
                currentPlan,
                evaluation,
                budget,
                Instant.now().plus(properties.getPlannerTimeout()));
    }

    public RetrievalPlan followUpPlan(
            RetrievalPlan currentPlan,
            EvidenceEvaluation evaluation,
            RetrievalBudget budget,
            Instant overallDeadline) {
        RetrievalPlan seed = ruleBasedPlanner.followUpPlan(currentPlan, evaluation, budget);
        if (seed.tasks().isEmpty()) {
            return seed;
        }
        Instant componentDeadline = Instant.now().plus(properties.getPlannerTimeout());
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(SYSTEM_PROMPT),
                        ChatMessage.user(gson.toJson(Map.of(
                                "maximumQueries", budget.maxSubQueries(),
                                "tasks", currentPlan.tasks(),
                                "gaps", evaluation.gaps(),
                                "ruleBasedQueries", seed.tasks())))))
                .temperature(0D)
                .topP(1D)
                .thinking(false)
                .maxTokens(700)
                .deadlineAt(earlier(componentDeadline, overallDeadline))
                .build();
        return parser.parse(llmService.chat(request), currentPlan, budget);
    }

    private Instant earlier(Instant first, Instant second) {
        return second != null && second.isBefore(first) ? second : first;
    }
}
