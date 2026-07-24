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

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RuleBasedRetrievalTaskPlanner implements RetrievalTaskPlanner {

    @Override
    public RetrievalPlan followUpPlan(
            RetrievalPlan currentPlan,
            EvidenceEvaluation evaluation,
            RetrievalBudget budget) {
        if (evaluation == null || evaluation.sufficient()) {
            return new RetrievalPlan(List.of(), "evidence is already sufficient");
        }
        Map<String, RetrievalTask> tasksById = new LinkedHashMap<>();
        currentPlan.tasks().forEach(task -> tasksById.put(task.taskId(), task));
        List<RetrievalTask> tasks = evaluation.gaps().stream()
                .limit(budget.maxSubQueries())
                .map(gap -> toFollowUpTask(tasksById.get(gap.taskId()), gap))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new RetrievalPlan(tasks, "rule-based follow-up for evidence gaps");
    }

    private RetrievalTask toFollowUpTask(RetrievalTask original, RetrievalGap gap) {
        if (original == null) {
            return null;
        }
        String query = gap.suggestedQuery();
        if (query == null || query.isBlank()) {
            query = original.question();
            if (!gap.missingFacts().isEmpty()) {
                query += " " + String.join(" ", gap.missingFacts());
            }
        }
        return new RetrievalTask(
                original.taskId(),
                query,
                original.knowledgeBaseIds(),
                gap.missingFacts().isEmpty() ? original.requiredFacts() : gap.missingFacts(),
                original.dependsOnPreviousEvidence());
    }
}
