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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceLedgerTests {

    @Test
    void shouldCreatePendingStateForEveryTask() {
        EvidenceLedger ledger = EvidenceLedger.empty(List.of(task("task-1"), task("task-2")));

        assertEquals(TaskEvidenceStatus.PENDING, ledger.taskStates().get("task-1").status());
        assertEquals(TaskEvidenceStatus.PENDING, ledger.taskStates().get("task-2").status());
        assertEquals(0, ledger.evidence().size());
    }

    @Test
    void shouldDeduplicateEvidenceByDocumentAndChunk() {
        EvidenceLedger ledger = EvidenceLedger.empty(List.of(task("task-1")));
        EvidenceItem first = evidence("task-1", "chunk-1", 1);
        EvidenceItem duplicate = evidence("task-1", "chunk-1", 2);

        EvidenceLedger merged = ledger.merge(
                List.of(first, duplicate),
                new RetrievalIteration(1, List.of("task-1"), 2, 1, 10));

        assertEquals(1, merged.evidence().size());
        assertEquals(1, merged.taskStates().get("task-1").evidenceKeys().size());
        assertEquals(TaskEvidenceStatus.PARTIALLY_SUPPORTED,
                merged.taskStates().get("task-1").status());
    }

    @Test
    void sameEvidenceCanSupportMoreThanOneTaskWithoutDuplicateStorage() {
        EvidenceLedger ledger = EvidenceLedger.empty(List.of(task("task-1"), task("task-2")));

        EvidenceLedger merged = ledger.merge(
                List.of(evidence("task-1", "chunk-1", 1), evidence("task-2", "chunk-1", 1)),
                null);

        assertEquals(1, merged.evidence().size());
        assertEquals(1, merged.taskStates().get("task-1").evidenceKeys().size());
        assertEquals(1, merged.taskStates().get("task-2").evidenceKeys().size());
    }

    @Test
    void shouldPreserveExplicitConflictStatus() {
        EvidenceLedger ledger = EvidenceLedger.empty(List.of(task("task-1")))
                .merge(List.of(evidence("task-1", "chunk-1", 1)), null)
                .withTaskStatus("task-1", TaskEvidenceStatus.CONFLICTED);

        assertEquals(TaskEvidenceStatus.CONFLICTED, ledger.taskStates().get("task-1").status());
        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.withTaskStatus("missing", TaskEvidenceStatus.SUPPORTED));
    }

    private RetrievalTask task(String id) {
        return new RetrievalTask(id, "question", List.of(), Set.of(), false);
    }

    private EvidenceItem evidence(String taskId, String chunkId, int iteration) {
        return new EvidenceItem(
                taskId,
                chunkId,
                "doc-1",
                "kb-1",
                "content",
                0.9,
                "source",
                null,
                iteration);
    }
}
