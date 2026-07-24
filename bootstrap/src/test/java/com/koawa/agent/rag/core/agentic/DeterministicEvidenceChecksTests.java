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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicEvidenceChecksTests {

    private final DeterministicEvidenceChecks checks = new DeterministicEvidenceChecks();

    @Test
    void emptyEvidenceIsUnsupported() {
        RetrievalTask task = task("task-1", Set.of("fact"));
        EvidenceEvaluation evaluation = checks.evaluate(
                new RetrievalPlan(List.of(task), "test"),
                EvidenceLedger.empty(List.of(task)));

        assertFalse(evaluation.sufficient());
        assertTrue(evaluation.assessments().stream()
                .allMatch(item -> item.status() == TaskEvidenceStatus.UNSUPPORTED));
        assertFalse(evaluation.gaps().isEmpty());
    }

    @Test
    void evidenceWithoutRequiredFactsIsDeterministicallySupported() {
        RetrievalTask task = task("task-1", Set.of());
        EvidenceLedger ledger = EvidenceLedger.empty(List.of(task)).merge(
                List.of(new EvidenceItem(
                        "task-1", "chunk-1", "doc-1", "kb-1", "content",
                        0.8, "source", null, 1)),
                null);

        EvidenceEvaluation evaluation = checks.evaluate(
                new RetrievalPlan(List.of(task), "test"), ledger);

        assertTrue(evaluation.sufficient());
        assertTrue(evaluation.gaps().isEmpty());
    }

    @Test
    void requiredFactsAreNotGuessedFromChunkCount() {
        RetrievalTask task = task("task-1", Set.of("fact"));
        EvidenceLedger ledger = EvidenceLedger.empty(List.of(task)).merge(
                List.of(new EvidenceItem(
                        "task-1", "chunk-1", "doc-1", "kb-1", "content",
                        0.8, "source", null, 1)),
                null);

        EvidenceEvaluation evaluation = checks.evaluate(
                new RetrievalPlan(List.of(task), "test"), ledger);

        assertFalse(evaluation.sufficient());
        assertTrue(evaluation.assessments().stream()
                .allMatch(item -> item.status() == TaskEvidenceStatus.PARTIALLY_SUPPORTED));
    }

    private RetrievalTask task(String id, Set<String> facts) {
        return new RetrievalTask(id, "question", List.of(), facts, false);
    }
}
