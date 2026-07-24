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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EvidenceLedger {

    private final Map<String, TaskEvidenceState> taskStates;
    private final List<EvidenceItem> evidence;
    private final List<RetrievalIteration> iterations;

    private EvidenceLedger(
            Map<String, TaskEvidenceState> taskStates,
            List<EvidenceItem> evidence,
            List<RetrievalIteration> iterations) {
        this.taskStates = Map.copyOf(taskStates);
        this.evidence = List.copyOf(evidence);
        this.iterations = List.copyOf(iterations);
    }

    public static EvidenceLedger empty(List<RetrievalTask> tasks) {
        Map<String, TaskEvidenceState> states = new LinkedHashMap<>();
        if (tasks != null) {
            tasks.forEach(task -> states.put(
                    task.taskId(),
                    new TaskEvidenceState(task.taskId(), TaskEvidenceStatus.PENDING, List.of())));
        }
        return new EvidenceLedger(states, List.of(), List.of());
    }

    public EvidenceLedger merge(List<EvidenceItem> additions, RetrievalIteration iteration) {
        Map<String, EvidenceItem> unique = new LinkedHashMap<>();
        evidence.forEach(item -> unique.put(item.deduplicationKey(), item));
        Map<String, List<String>> keysByTask = new LinkedHashMap<>();
        evidence.forEach(item -> addTaskEvidenceKey(keysByTask, item));
        if (additions != null) {
            additions.forEach(item -> {
                unique.putIfAbsent(item.deduplicationKey(), item);
                addTaskEvidenceKey(keysByTask, item);
            });
        }
        Map<String, TaskEvidenceState> states = new LinkedHashMap<>(taskStates);
        keysByTask.forEach((taskId, keys) -> states.put(
                taskId,
                new TaskEvidenceState(taskId, TaskEvidenceStatus.PARTIALLY_SUPPORTED, keys)));
        List<RetrievalIteration> mergedIterations = new ArrayList<>(iterations);
        if (iteration != null) {
            mergedIterations.add(iteration);
        }
        return new EvidenceLedger(states, new ArrayList<>(unique.values()), mergedIterations);
    }

    private static void addTaskEvidenceKey(
            Map<String, List<String>> keysByTask,
            EvidenceItem item) {
        List<String> keys = keysByTask.computeIfAbsent(item.taskId(), ignored -> new ArrayList<>());
        if (!keys.contains(item.deduplicationKey())) {
            keys.add(item.deduplicationKey());
        }
    }

    public EvidenceLedger withTaskStatus(String taskId, TaskEvidenceStatus status) {
        TaskEvidenceState current = taskStates.get(taskId);
        if (current == null) {
            throw new IllegalArgumentException("unknown retrieval task: " + taskId);
        }
        Map<String, TaskEvidenceState> states = new LinkedHashMap<>(taskStates);
        states.put(taskId, new TaskEvidenceState(taskId, status, current.evidenceKeys()));
        return new EvidenceLedger(states, evidence, iterations);
    }

    public Map<String, TaskEvidenceState> taskStates() {
        return taskStates;
    }

    public List<EvidenceItem> evidence() {
        return evidence;
    }

    public List<RetrievalIteration> iterations() {
        return iterations;
    }
}
