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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LlmEvidenceEvaluator implements EvidenceEvaluator {

    private static final String SYSTEM_PROMPT = """
            You evaluate retrieved evidence only. Never answer the user's question.
            Return exactly one JSON object with: sufficient, assessments, gaps,
            confidence, explanation. Each assessment must contain taskId, status,
            coveredFacts, missingFacts, explanation. Status must be one of
            SUPPORTED, PARTIALLY_SUPPORTED, UNSUPPORTED, CONFLICTED.
            Use only supplied evidence. Assess every task exactly once and never
            invent a taskId. A task is SUPPORTED only when all requiredFacts are
            directly supported. Conflicting sources must be CONFLICTED.
            """;

    private final LLMService llmService;
    private final DeterministicEvidenceChecks deterministicChecks;
    private final EvidenceEvaluationParser parser;
    private final AgenticRetrievalProperties properties;
    private final Gson gson = new Gson();

    @Override
    public EvidenceEvaluation evaluate(RetrievalPlan plan, EvidenceLedger ledger) {
        EvidenceEvaluation deterministic = deterministicChecks.evaluate(plan, ledger);
        if (deterministic.sufficient() || ledger.evidence().isEmpty()) {
            return deterministic;
        }
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(SYSTEM_PROMPT),
                        ChatMessage.user(gson.toJson(buildPayload(plan, ledger)))))
                .temperature(0D)
                .topP(1D)
                .thinking(false)
                .maxTokens(1200)
                .deadlineAt(Instant.now().plus(properties.getEvaluatorTimeout()))
                .build();
        return parser.parse(llmService.chat(request), plan);
    }

    private Map<String, Object> buildPayload(RetrievalPlan plan, EvidenceLedger ledger) {
        List<Map<String, Object>> tasks = plan.tasks().stream()
                .map(task -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("taskId", task.taskId());
                    value.put("question", task.question());
                    value.put("requiredFacts", task.requiredFacts());
                    return value;
                })
                .toList();
        List<Map<String, Object>> evidence = ledger.evidence().stream()
                .limit(properties.getMaxEvidenceItems())
                .map(item -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("taskId", item.taskId());
                    value.put("chunkId", item.chunkId());
                    value.put("documentId", item.documentId());
                    value.put("sourceTitle", item.sourceTitle());
                    value.put("content", truncate(item.content(), properties.getMaxEvidenceChars()));
                    return value;
                })
                .toList();
        return Map.of("tasks", tasks, "evidence", evidence);
    }

    private String truncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars);
    }
}
