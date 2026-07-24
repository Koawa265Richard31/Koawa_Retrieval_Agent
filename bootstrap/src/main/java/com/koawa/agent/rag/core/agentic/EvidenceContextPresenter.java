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

import com.koawa.agent.rag.dto.RetrievalContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EvidenceContextPresenter {

    private final EvidenceCitationMapper citationMapper;

    public RetrievalContext present(AgenticRetrievalResult result) {
        if (result == null || result.retrievalContext() == null
                || result.evidenceLedger() == null) {
            return result == null ? null : result.retrievalContext();
        }
        List<EvidenceItem> evidence = result.evidenceLedger().evidence();
        List<EvidenceCitation> citations = citationMapper.map(evidence);
        Map<String, EvidenceCitation> byKey = new LinkedHashMap<>();
        for (EvidenceCitation citation : citations) {
            byKey.put(citation.documentId() + ":" + citation.chunkId(), citation);
        }
        String indexed = evidence.stream()
                .filter(item -> byKey.containsKey(item.deduplicationKey()))
                .map(item -> {
                    EvidenceCitation citation = byKey.get(item.deduplicationKey());
                    return "[" + citation.citationId() + "] "
                            + safe(citation.sourceTitle(), citation.documentId())
                            + "\n" + safe(item.content(), "");
                })
                .collect(Collectors.joining("\n\n"));
        List<String> conflicts = result.evidenceLedger().taskStates().values().stream()
                .filter(state -> state.status() == TaskEvidenceStatus.CONFLICTED)
                .map(TaskEvidenceState::taskId)
                .toList();
        StringBuilder body = new StringBuilder();
        body.append("引用规则：事实性结论必须使用下列真实编号，如 [E1]；")
                .append("没有对应证据时不得编造编号。");
        if (!conflicts.isEmpty()) {
            body.append("\n冲突提示：任务 ")
                    .append(String.join(", ", conflicts))
                    .append(" 的证据互相冲突，必须明确说明分歧，不能输出单一确定结论。");
        }
        if (!indexed.isBlank()) {
            body.append("\n\n").append(indexed);
        }
        RetrievalContext original = result.retrievalContext();
        return RetrievalContext.builder()
                .mcpContext(original.getMcpContext())
                .kbContext(body.toString())
                .intentChunks(original.getIntentChunks())
                .citations(citations)
                .conflictedTaskIds(conflicts)
                .build();
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
