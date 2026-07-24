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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.koawa.agent.knowledge.dao.entity.KnowledgeBaseDO;
import com.koawa.agent.knowledge.dao.entity.KnowledgeChunkDO;
import com.koawa.agent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.koawa.agent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.koawa.agent.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.koawa.agent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.koawa.agent.rag.config.AgenticRetrievalProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FullDocumentExpander {

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentAccessPolicy accessPolicy;
    private final AgenticRetrievalProperties properties;

    public FullDocumentExpansion expand(
            EvidenceItem hit,
            RetrievalAccessPrincipal principal) {
        if (!properties.isFullDocumentExpansionEnabled() || hit == null) {
            return empty(hit);
        }
        KnowledgeChunkDO hitChunk = chunkMapper.selectById(hit.chunkId());
        KnowledgeDocumentDO document = documentMapper.selectById(hit.documentId());
        if (!validHit(hit, hitChunk, document)) {
            return empty(hit);
        }
        KnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectById(document.getKbId());
        if (!accessPolicy.canRead(principal, knowledgeBase)) {
            return empty(hit);
        }
        List<KnowledgeChunkDO> chunks = chunkMapper.selectList(
                Wrappers.<KnowledgeChunkDO>lambdaQuery()
                        .eq(KnowledgeChunkDO::getDocId, document.getId())
                        .eq(KnowledgeChunkDO::getKbId, knowledgeBase.getId())
                        .eq(KnowledgeChunkDO::getEnabled, 1)
                        .orderByAsc(KnowledgeChunkDO::getChunkIndex));
        int remaining = Math.max(0, properties.getMaxFullDocumentChars());
        boolean truncated = false;
        List<EvidenceItem> evidence = new ArrayList<>();
        for (KnowledgeChunkDO chunk : chunks) {
            String content = chunk.getContent() == null ? "" : chunk.getContent();
            if (remaining == 0) {
                truncated = true;
                break;
            }
            if (content.length() > remaining) {
                content = content.substring(0, remaining);
                truncated = true;
            }
            evidence.add(new EvidenceItem(
                    hit.taskId(), chunk.getId(), document.getId(),
                    knowledgeBase.getId(), content, hit.score(),
                    hit.sourceTitle(), hit.sourceUri(), hit.iteration()));
            remaining -= content.length();
        }
        return new FullDocumentExpansion(
                document.getId(), List.copyOf(evidence), truncated);
    }

    private boolean validHit(
            EvidenceItem hit,
            KnowledgeChunkDO chunk,
            KnowledgeDocumentDO document) {
        return chunk != null && document != null
                && hit.documentId().equals(chunk.getDocId())
                && document.getId().equals(chunk.getDocId())
                && document.getKbId() != null
                && document.getKbId().equals(chunk.getKbId())
                && document.getKbId().equals(hit.knowledgeBaseId())
                && Integer.valueOf(1).equals(chunk.getEnabled())
                && Integer.valueOf(1).equals(document.getEnabled());
    }

    private FullDocumentExpansion empty(EvidenceItem hit) {
        return new FullDocumentExpansion(
                hit == null ? null : hit.documentId(), List.of(), false);
    }
}
