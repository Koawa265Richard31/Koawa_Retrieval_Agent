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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Cheap evidence checks that never infer semantic fact coverage.
 */
@Component
public class DeterministicEvidenceChecks implements EvidenceEvaluator {

    @Override
    public EvidenceEvaluation evaluate(RetrievalPlan plan, EvidenceLedger ledger) {
        List<TaskAssessment> assessments = new ArrayList<>();
        List<RetrievalGap> gaps = new ArrayList<>();
        for (RetrievalTask task : plan.tasks()) {
            TaskEvidenceState state = ledger.taskStates().get(task.taskId());
            boolean empty = state == null || state.evidenceKeys().isEmpty();
            TaskEvidenceStatus status;
            Set<String> missingFacts;
            if (empty) {
                status = TaskEvidenceStatus.UNSUPPORTED;
                missingFacts = task.requiredFacts();
            } else if (state.status() == TaskEvidenceStatus.CONFLICTED) {
                status = TaskEvidenceStatus.CONFLICTED;
                missingFacts = task.requiredFacts();
            } else if (task.requiredFacts().isEmpty()) {
                status = TaskEvidenceStatus.SUPPORTED;
                missingFacts = Set.of();
            } else {
                status = TaskEvidenceStatus.PARTIALLY_SUPPORTED;
                missingFacts = task.requiredFacts();
            }
            assessments.add(new TaskAssessment(
                    task.taskId(),
                    status,
                    Set.of(),
                    missingFacts,
                    deterministicExplanation(status)));
            if (status != TaskEvidenceStatus.SUPPORTED) {
                gaps.add(new RetrievalGap(task.taskId(), missingFacts, task.question()));
            }
        }
        boolean sufficient = !assessments.isEmpty()
                && assessments.stream().allMatch(
                        assessment -> assessment.status() == TaskEvidenceStatus.SUPPORTED);
        return new EvidenceEvaluation(
                sufficient,
                assessments,
                gaps,
                sufficient ? 1.0 : 0.0,
                sufficient
                        ? "All tasks passed deterministic evidence checks."
                        : "Semantic evidence evaluation is required.");
    }

    private String deterministicExplanation(TaskEvidenceStatus status) {
        return switch (status) {
            case SUPPORTED -> "Task has evidence and declares no semantic fact requirements.";
            case UNSUPPORTED -> "Task has no evidence.";
            case CONFLICTED -> "Task evidence is marked as conflicted.";
            default -> "Task has evidence, but required fact coverage is not yet evaluated.";
        };
    }
}
