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

import com.koawa.agent.framework.convention.RetrievedChunk;
import com.koawa.agent.framework.trace.RagTraceNode;
import com.koawa.agent.infra.http.ModelClientErrorType;
import com.koawa.agent.infra.http.ModelClientException;
import com.koawa.agent.rag.config.AgenticRetrievalProperties;
import com.koawa.agent.rag.dto.RetrievalContext;
import com.koawa.agent.rag.dto.SubQuestionIntent;
import com.koawa.agent.rag.service.handler.StreamTaskManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class DefaultAgenticRetrievalOrchestrator implements AgenticRetrievalOrchestrator {

    private final RetrievalPlanFactory planFactory;
    private final RetrievalContextEvidenceAdapter evidenceAdapter;
    private final LlmEvidenceEvaluator evidenceEvaluator;
    private final LlmRetrievalTaskPlanner taskPlanner;
    private final AgenticRetrievalIterationExecutor retrievalExecutor;
    private final StreamTaskManager taskManager;
    private final AgenticRetrievalProperties properties;
    private final FullDocumentExpander documentExpander;

    @Override
    @RagTraceNode(name = "agentic-retrieval-orchestrator", type = "RETRIEVAL_ITERATION")
    public AgenticRetrievalResult execute(
            String taskId,
            List<SubQuestionIntent> subIntents,
            RetrievalContext initialContext,
            int topK) {
        return execute(taskId, subIntents, initialContext, topK, null);
    }

    @Override
    public AgenticRetrievalResult execute(
            String taskId,
            List<SubQuestionIntent> subIntents,
            RetrievalContext initialContext,
            int topK,
            RetrievalAccessPrincipal principal) {
        RetrievalBudget budget = budget();
        Instant startedAt = Instant.now();
        Instant deadline = startedAt.plus(budget.timeout());
        RetrievalPlanSeed seed = planFactory.initialPlan(subIntents);
        List<EvidenceItem> initialEvidence = limited(
                evidenceAdapter.adapt(initialContext, seed.taskIdByIntentId(), 1),
                budget.maxRetrievedChunks());
        EvidenceLedger ledger = EvidenceLedger.empty(seed.plan().tasks()).merge(
                initialEvidence,
                iteration(1, seed.plan(), initialEvidence.size(), initialEvidence.size(), startedAt));

        EvidenceEvaluation evaluation;
        try {
            evaluation = evidenceEvaluator.evaluate(seed.plan(), ledger, deadline);
        } catch (RuntimeException exception) {
            return result(
                    initialContext,
                    ledger,
                    failureReason(exception, deadline, RetrievalStopReason.EVALUATION_FAILED),
                    1,
                    false);
        }
        ledger = applyEvaluation(ledger, evaluation);
        if (!evaluation.sufficient()
                && requestsFullDocumentContext(evaluation)) {
            List<EvidenceItem> expanded = expandDocuments(
                    ledger, evaluation, principal, budget.maxRetrievedChunks());
            if (!expanded.isEmpty()) {
                ledger = ledger.merge(expanded, null);
                try {
                    evaluation = evidenceEvaluator.evaluate(seed.plan(), ledger, deadline);
                    ledger = applyEvaluation(ledger, evaluation);
                } catch (RuntimeException exception) {
                    return result(
                            initialContext, ledger,
                            failureReason(
                                    exception, deadline,
                                    RetrievalStopReason.EVALUATION_FAILED),
                            1, false);
                }
            }
        }
        if (evaluation.sufficient()) {
            return result(initialContext, ledger, RetrievalStopReason.SUFFICIENT, 1, true);
        }
        RetrievalStopReason guard = guard(taskId, deadline, budget, ledger);
        if (guard != null) {
            return result(initialContext, ledger, guard, 1, false);
        }

        RetrievalPlan followUpPlan;
        try {
            followUpPlan = taskPlanner.followUpPlan(
                    seed.plan(), evaluation, budget, deadline);
        } catch (RuntimeException exception) {
            return result(
                    initialContext,
                    ledger,
                    failureReason(exception, deadline, RetrievalStopReason.PLANNING_FAILED),
                    1,
                    false);
        }
        if (followUpPlan == null) {
            return result(initialContext, ledger, RetrievalStopReason.PLANNING_FAILED, 1, false);
        }
        if (followUpPlan.tasks().isEmpty()) {
            return result(initialContext, ledger, RetrievalStopReason.NO_NEW_EVIDENCE, 1, false);
        }
        if (hasDuplicateQuery(seed.plan(), followUpPlan)) {
            return result(initialContext, ledger, RetrievalStopReason.DUPLICATE_QUERY, 1, false);
        }
        guard = guard(taskId, deadline, budget, ledger);
        if (guard != null) {
            return result(initialContext, ledger, guard, 1, false);
        }

        RetrievalContext followUpContext;
        Instant secondStartedAt = Instant.now();
        try {
            List<SubQuestionIntent> followUpIntents = planFactory.toSubIntents(followUpPlan, seed);
            followUpContext = retrievalExecutor.retrieve(
                    followUpIntents,
                    topK,
                    deadline,
                    () -> taskManager.isCancelled(taskId));
        } catch (CancellationException exception) {
            return result(initialContext, ledger, RetrievalStopReason.CANCELLED, 1, false);
        } catch (TimeoutException exception) {
            return result(initialContext, ledger, RetrievalStopReason.TIMEOUT, 1, false);
        } catch (ExecutionException exception) {
            return result(initialContext, ledger, RetrievalStopReason.RETRIEVAL_FAILED, 1, false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return result(initialContext, ledger, RetrievalStopReason.CANCELLED, 1, false);
        }
        if (taskManager.isCancelled(taskId)) {
            return result(initialContext, ledger, RetrievalStopReason.CANCELLED, 1, false);
        }
        if (Instant.now().isAfter(deadline)) {
            return result(initialContext, ledger, RetrievalStopReason.TIMEOUT, 1, false);
        }

        int remaining = budget.maxRetrievedChunks() - ledger.evidence().size();
        List<EvidenceItem> additions = limited(
                evidenceAdapter.adapt(followUpContext, seed.taskIdByIntentId(), 2),
                Math.max(remaining, 0));
        int newEvidenceCount = countNewEvidence(ledger, additions);
        if (newEvidenceCount == 0) {
            return result(initialContext, ledger, RetrievalStopReason.NO_NEW_EVIDENCE, 2, false);
        }
        ledger = ledger.merge(
                additions,
                iteration(2, followUpPlan, additions.size(), newEvidenceCount, secondStartedAt));
        RetrievalContext mergedContext = mergeContexts(initialContext, followUpContext);
        try {
            evaluation = evidenceEvaluator.evaluate(seed.plan(), ledger, deadline);
        } catch (RuntimeException exception) {
            return result(
                    mergedContext,
                    ledger,
                    failureReason(exception, deadline, RetrievalStopReason.EVALUATION_FAILED),
                    2,
                    false);
        }
        ledger = applyEvaluation(ledger, evaluation);
        return result(
                mergedContext,
                ledger,
                evaluation.sufficient()
                        ? RetrievalStopReason.SUFFICIENT
                        : RetrievalStopReason.BUDGET_EXHAUSTED,
                2,
                evaluation.sufficient());
    }

    private boolean requestsFullDocumentContext(EvidenceEvaluation evaluation) {
        return properties.isFullDocumentExpansionEnabled()
                && evaluation.gaps().stream()
                .flatMap(gap -> gap.missingFacts().stream())
                .anyMatch("FULL_DOCUMENT_CONTEXT"::equalsIgnoreCase);
    }

    private List<EvidenceItem> expandDocuments(
            EvidenceLedger ledger,
            EvidenceEvaluation evaluation,
            RetrievalAccessPrincipal principal,
            int maximumChunks) {
        Set<String> requestedTasks = evaluation.gaps().stream()
                .filter(gap -> gap.missingFacts().stream()
                        .anyMatch("FULL_DOCUMENT_CONTEXT"::equalsIgnoreCase))
                .map(RetrievalGap::taskId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, EvidenceItem> additions = new LinkedHashMap<>();
        for (EvidenceItem hit : ledger.evidence()) {
            if (!requestedTasks.contains(hit.taskId())) {
                continue;
            }
            FullDocumentExpansion expansion = documentExpander.expand(hit, principal);
            expansion.evidence().forEach(
                    item -> additions.putIfAbsent(item.deduplicationKey(), item));
            if (ledger.evidence().size() + additions.size() >= maximumChunks) {
                break;
            }
        }
        int remaining = Math.max(0, maximumChunks - ledger.evidence().size());
        return additions.values().stream().limit(remaining).toList();
    }

    private RetrievalBudget budget() {
        return new RetrievalBudget(
                properties.getMaxIterations(),
                properties.getMaxSubQueries(),
                properties.getMaxRetrievedChunks(),
                properties.getTimeout());
    }

    private EvidenceLedger applyEvaluation(
            EvidenceLedger ledger,
            EvidenceEvaluation evaluation) {
        EvidenceLedger updated = ledger;
        for (TaskAssessment assessment : evaluation.assessments()) {
            if (updated.taskStates().containsKey(assessment.taskId())) {
                updated = updated.withTaskStatus(
                        assessment.taskId(),
                        assessment.status());
            }
        }
        return updated;
    }

    private RetrievalStopReason failureReason(
            RuntimeException exception,
            Instant deadline,
            RetrievalStopReason fallback) {
        if (Instant.now().isAfter(deadline)) {
            return RetrievalStopReason.TIMEOUT;
        }
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ModelClientException modelException
                    && modelException.getErrorType() == ModelClientErrorType.DEADLINE_EXCEEDED) {
                return RetrievalStopReason.TIMEOUT;
            }
            current = current.getCause();
        }
        return fallback;
    }

    private RetrievalStopReason guard(
            String taskId,
            Instant deadline,
            RetrievalBudget budget,
            EvidenceLedger ledger) {
        if (taskManager.isCancelled(taskId)) {
            return RetrievalStopReason.CANCELLED;
        }
        if (Instant.now().isAfter(deadline)) {
            return RetrievalStopReason.TIMEOUT;
        }
        if (budget.maxIterations() < 2
                || ledger.evidence().size() >= budget.maxRetrievedChunks()) {
            return RetrievalStopReason.BUDGET_EXHAUSTED;
        }
        return null;
    }

    private boolean hasDuplicateQuery(RetrievalPlan initial, RetrievalPlan followUp) {
        Set<String> seen = new LinkedHashSet<>();
        initial.tasks().stream()
                .map(RetrievalTask::question)
                .map(this::normalize)
                .forEach(seen::add);
        for (RetrievalTask task : followUp.tasks()) {
            if (!seen.add(normalize(task.question()))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private List<EvidenceItem> limited(List<EvidenceItem> items, int maximum) {
        if (items == null || maximum <= 0) {
            return List.of();
        }
        return items.stream().limit(maximum).toList();
    }

    private int countNewEvidence(EvidenceLedger ledger, List<EvidenceItem> additions) {
        Set<String> existing = new LinkedHashSet<>();
        ledger.evidence().forEach(item -> existing.add(item.deduplicationKey()));
        return (int) additions.stream()
                .map(EvidenceItem::deduplicationKey)
                .filter(existing::add)
                .count();
    }

    private RetrievalIteration iteration(
            int number,
            RetrievalPlan plan,
            int retrieved,
            int additions,
            Instant startedAt) {
        return new RetrievalIteration(
                number,
                plan.tasks().stream().map(RetrievalTask::taskId).toList(),
                retrieved,
                additions,
                Math.max(0, Duration.between(startedAt, Instant.now()).toMillis()));
    }

    private RetrievalContext mergeContexts(
            RetrievalContext initial,
            RetrievalContext followUp) {
        Map<String, List<RetrievedChunk>> chunks = new LinkedHashMap<>();
        mergeChunks(chunks, initial == null ? null : initial.getIntentChunks());
        mergeChunks(chunks, followUp == null ? null : followUp.getIntentChunks());
        return RetrievalContext.builder()
                .kbContext(joinContext(
                        initial == null ? null : initial.getKbContext(),
                        followUp == null ? null : followUp.getKbContext()))
                .mcpContext(joinContext(
                        initial == null ? null : initial.getMcpContext(),
                        followUp == null ? null : followUp.getMcpContext()))
                .intentChunks(chunks)
                .build();
    }

    private void mergeChunks(
            Map<String, List<RetrievedChunk>> target,
            Map<String, List<RetrievedChunk>> source) {
        if (source == null) {
            return;
        }
        source.forEach((intentId, values) -> {
            Map<String, RetrievedChunk> unique = new LinkedHashMap<>();
            target.getOrDefault(intentId, List.of()).forEach(
                    item -> unique.put(item.getId(), item));
            if (values != null) {
                values.forEach(item -> unique.putIfAbsent(item.getId(), item));
            }
            target.put(intentId, new ArrayList<>(unique.values()));
        });
    }

    private String joinContext(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + "\n" + second;
    }

    private AgenticRetrievalResult result(
            RetrievalContext context,
            EvidenceLedger ledger,
            RetrievalStopReason stopReason,
            int iterations,
            boolean sufficient) {
        return new AgenticRetrievalResult(context, ledger, stopReason, iterations, sufficient);
    }
}
