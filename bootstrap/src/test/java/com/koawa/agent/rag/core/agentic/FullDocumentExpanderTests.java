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

import com.koawa.agent.knowledge.dao.entity.KnowledgeBaseDO;
import com.koawa.agent.knowledge.dao.entity.KnowledgeChunkDO;
import com.koawa.agent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.koawa.agent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.koawa.agent.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.koawa.agent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.koawa.agent.rag.config.AgenticRetrievalProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FullDocumentExpanderTests {

    private final KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
    private final KnowledgeDocumentMapper documentMapper =
            mock(KnowledgeDocumentMapper.class);
    private final KnowledgeBaseMapper baseMapper = mock(KnowledgeBaseMapper.class);
    private final AgenticRetrievalProperties properties =
            new AgenticRetrievalProperties();

    @Test
    void deniedAclFailsClosedBeforeLoadingDocumentChunks() {
        prepareHit();
        FullDocumentExpander expander = new FullDocumentExpander(
                chunkMapper, documentMapper, baseMapper,
                (principal, base) -> false, properties);

        assertTrue(expander.expand(hit(), principal()).evidence().isEmpty());
        verify(chunkMapper, never()).selectList(any());
    }

    @Test
    void expandsOnlyVerifiedHitDocumentWithinCharacterBudget() {
        prepareHit();
        properties.setMaxFullDocumentChars(5);
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk("chunk-1", "abc"),
                chunk("chunk-2", "def")));
        FullDocumentExpander expander = new FullDocumentExpander(
                chunkMapper, documentMapper, baseMapper,
                (principal, base) -> true, properties);

        FullDocumentExpansion expansion = expander.expand(hit(), principal());

        assertEquals(List.of("abc", "de"),
                expansion.evidence().stream().map(EvidenceItem::content).toList());
        assertTrue(expansion.truncated());
    }

    private void prepareHit() {
        when(chunkMapper.selectById("chunk-1")).thenReturn(chunk("chunk-1", "abc"));
        when(documentMapper.selectById("doc-1")).thenReturn(
                KnowledgeDocumentDO.builder()
                        .id("doc-1").kbId("kb-1").enabled(1).build());
        when(baseMapper.selectById("kb-1")).thenReturn(
                KnowledgeBaseDO.builder().id("kb-1").createdBy("alice").build());
    }

    private KnowledgeChunkDO chunk(String id, String content) {
        return KnowledgeChunkDO.builder()
                .id(id).docId("doc-1").kbId("kb-1")
                .enabled(1).content(content).build();
    }

    private EvidenceItem hit() {
        return new EvidenceItem(
                "task-1", "chunk-1", "doc-1", "kb-1",
                "abc", 0.9, "source", null, 1);
    }

    private RetrievalAccessPrincipal principal() {
        return new RetrievalAccessPrincipal("user-1", "alice", "user");
    }
}
