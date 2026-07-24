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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceCitationMapperTests {

    @Test
    void mapsOnlyExistingEvidenceAndNeverAcceptsInventedId() {
        EvidenceCitationMapper mapper = new EvidenceCitationMapper();
        List<EvidenceCitation> citations = mapper.map(List.of(
                evidence("chunk-1", "doc-1"),
                evidence("chunk-1", "doc-1"),
                evidence("chunk-2", "doc-2")));

        assertEquals(List.of("E1", "E2"),
                citations.stream().map(EvidenceCitation::citationId).toList());
        assertTrue(mapper.referencesExistingCitation("E2", citations));
        assertFalse(mapper.referencesExistingCitation("E3", citations));
    }

    @Test
    void conflictedTaskProducesExplicitPromptWarning() {
        RetrievalTask task = new RetrievalTask(
                "task-1", "question", List.of(), Set.of(), false);
        EvidenceLedger ledger = EvidenceLedger.empty(List.of(task))
                .merge(List.of(evidence("chunk-1", "doc-1")), null)
                .withTaskStatus("task-1", TaskEvidenceStatus.CONFLICTED);
        AgenticRetrievalResult result = new AgenticRetrievalResult(
                com.koawa.agent.rag.dto.RetrievalContext.builder()
                        .kbContext("old").build(),
                ledger, RetrievalStopReason.SUFFICIENT, 1, true);

        com.koawa.agent.rag.dto.RetrievalContext presented =
                new EvidenceContextPresenter(new EvidenceCitationMapper())
                        .present(result);

        assertTrue(presented.getKbContext().contains("[E1]"));
        assertTrue(presented.getKbContext().contains("证据互相冲突"));
        assertEquals(List.of("task-1"), presented.getConflictedTaskIds());
    }

    private EvidenceItem evidence(String chunkId, String documentId) {
        return new EvidenceItem(
                "task-1", chunkId, documentId, "kb-1",
                "content", 0.9, "source", "https://example.test", 1);
    }
}
