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
import com.koawa.agent.knowledge.dao.entity.KnowledgeChunkDO;
import com.koawa.agent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.koawa.agent.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.koawa.agent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.koawa.agent.rag.dto.RetrievalContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Converts ACL-filtered retrieval output into stable, source-aware evidence.
 */
@Component
@RequiredArgsConstructor
public class RetrievalContextEvidenceAdapter {

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;

    public List<EvidenceItem> adapt(
            RetrievalContext context,
            Map<String, String> taskIdByIntentId,
            int iteration) {
        if (context == null || context.getIntentChunks() == null
                || context.getIntentChunks().isEmpty()) {
            return List.of();
        }
        Map<String, RetrievedChunk> retrievedById = new LinkedHashMap<>();
        context.getIntentChunks().values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .filter(chunk -> chunk.getId() != null && !chunk.getId().isBlank())
                .forEach(chunk -> retrievedById.putIfAbsent(chunk.getId(), chunk));
        if (retrievedById.isEmpty()) {
            return List.of();
        }
        Map<String, KnowledgeChunkDO> chunks = new LinkedHashMap<>();
        chunkMapper.selectByIds(retrievedById.keySet()).forEach(
                chunk -> chunks.put(chunk.getId(), chunk));
        List<String> documentIds = chunks.values().stream()
                .map(KnowledgeChunkDO::getDocId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, KnowledgeDocumentDO> documents = new LinkedHashMap<>();
        if (!documentIds.isEmpty()) {
            documentMapper.selectByIds(documentIds).forEach(
                    document -> documents.put(document.getId(), document));
        }

        Map<String, EvidenceItem> evidence = new LinkedHashMap<>();
        context.getIntentChunks().forEach((intentId, retrievedChunks) -> {
            String taskId = taskIdByIntentId == null
                    ? intentId
                    : taskIdByIntentId.getOrDefault(intentId, intentId);
            if (taskId == null || taskId.isBlank() || retrievedChunks == null) {
                return;
            }
            for (RetrievedChunk retrieved : retrievedChunks) {
                KnowledgeChunkDO chunk = retrieved == null ? null : chunks.get(retrieved.getId());
                if (chunk == null || chunk.getDocId() == null) {
                    continue;
                }
                KnowledgeDocumentDO document = documents.get(chunk.getDocId());
                String sourceTitle = document == null ? null : document.getDocName();
                String sourceUri = document == null
                        ? null
                        : firstNonBlank(document.getSourceLocation(), document.getFileUrl());
                String knowledgeBaseId = chunk.getKbId() != null
                        ? chunk.getKbId()
                        : document == null ? null : document.getKbId();
                EvidenceItem item = new EvidenceItem(
                        taskId,
                        retrieved.getId(),
                        chunk.getDocId(),
                        knowledgeBaseId,
                        retrieved.getText(),
                        retrieved.getScore() == null ? 0.0 : retrieved.getScore(),
                        sourceTitle,
                        sourceUri,
                        iteration);
                evidence.putIfAbsent(taskId + ":" + item.deduplicationKey(), item);
            }
        });
        return List.copyOf(evidence.values());
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
