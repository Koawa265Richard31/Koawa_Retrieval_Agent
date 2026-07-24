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

import com.koawa.agent.framework.trace.RagTraceNode;
import com.koawa.agent.rag.core.intent.NodeScore;
import com.koawa.agent.rag.dto.RetrievalContext;
import com.koawa.agent.rag.dto.SubQuestionIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgenticRetrievalShadowRunner {

    private final RetrievalContextEvidenceAdapter adapter;
    private final LlmEvidenceEvaluator evaluator;

    @RagTraceNode(name = "agentic-retrieval-shadow", type = "EVIDENCE_EVALUATION")
    public void evaluate(List<SubQuestionIntent> subIntents, RetrievalContext context) {
        ShadowPlan shadowPlan = buildPlan(subIntents, context);
        EvidenceLedger ledger = EvidenceLedger.empty(shadowPlan.plan().tasks())
                .merge(adapter.adapt(context, shadowPlan.taskIdByIntentId(), 1), null);
        EvidenceEvaluation evaluation = evaluator.evaluate(shadowPlan.plan(), ledger);
        Map<String, TaskEvidenceStatus> taskStatuses = new LinkedHashMap<>();
        evaluation.assessments().forEach(assessment ->
                taskStatuses.put(assessment.taskId(), assessment.status()));
        log.info(
                "Agentic Retrieval shadow completed: sufficient={}, taskStatuses={}, evidence={}, gaps={}, confidence={}",
                evaluation.sufficient(),
                taskStatuses,
                ledger.evidence().size(),
                evaluation.gaps().size(),
                evaluation.confidence());
    }

    private ShadowPlan buildPlan(
            List<SubQuestionIntent> subIntents,
            RetrievalContext context) {
        Map<String, String> questionByIntent = new LinkedHashMap<>();
        if (subIntents != null) {
            for (SubQuestionIntent subIntent : subIntents) {
                if (subIntent.nodeScores() == null) {
                    continue;
                }
                for (NodeScore score : subIntent.nodeScores()) {
                    if (score != null && score.getNode() != null) {
                        questionByIntent.putIfAbsent(
                                score.getNode().getId(), subIntent.subQuestion());
                    }
                }
            }
        }
        List<RetrievalTask> tasks = new ArrayList<>();
        Map<String, String> taskIds = new LinkedHashMap<>();
        if (context != null && context.getIntentChunks() != null) {
            context.getIntentChunks().keySet().forEach(intentId -> {
                String taskId = "shadow-" + intentId;
                String question = questionByIntent.getOrDefault(
                        intentId, firstQuestion(subIntents));
                tasks.add(new RetrievalTask(
                        taskId, question, List.of(), Set.of(question), false));
                taskIds.put(intentId, taskId);
            });
        }
        return new ShadowPlan(new RetrievalPlan(tasks, "shadow observation"), taskIds);
    }

    private String firstQuestion(List<SubQuestionIntent> subIntents) {
        if (subIntents == null || subIntents.isEmpty()
                || subIntents.get(0).subQuestion() == null
                || subIntents.get(0).subQuestion().isBlank()) {
            return "Evaluate retrieved evidence";
        }
        return subIntents.get(0).subQuestion();
    }

    private record ShadowPlan(
            RetrievalPlan plan,
            Map<String, String> taskIdByIntentId) {
    }
}
