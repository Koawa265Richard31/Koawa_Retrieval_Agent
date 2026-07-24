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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalContextEvidenceAdapterTests {

    @Test
    void shouldBatchResolveStableEvidenceMetadata() {
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        when(chunkMapper.selectByIds(anyCollection())).thenReturn(List.of(
                KnowledgeChunkDO.builder()
                        .id("chunk-1").docId("doc-1").kbId("kb-1").build()));
        when(documentMapper.selectByIds(anyCollection())).thenReturn(List.of(
                KnowledgeDocumentDO.builder()
                        .id("doc-1").kbId("kb-1").docName("policy.md")
                        .sourceLocation("https://example.test/policy").build()));
        RetrievalContext context = RetrievalContext.builder()
                .intentChunks(Map.of(
                        "intent-1",
                        List.of(RetrievedChunk.builder()
                                .id("chunk-1").text("evidence").score(0.9F).build())))
                .build();

        List<EvidenceItem> result = new RetrievalContextEvidenceAdapter(
                chunkMapper, documentMapper).adapt(
                context, Map.of("intent-1", "task-1"), 1);

        assertEquals(1, result.size());
        assertEquals("task-1", result.get(0).taskId());
        assertEquals("doc-1", result.get(0).documentId());
        assertEquals("policy.md", result.get(0).sourceTitle());
        assertEquals("https://example.test/policy", result.get(0).sourceUri());
        verify(chunkMapper).selectByIds(anyCollection());
        verify(documentMapper).selectByIds(anyCollection());
    }

    @Test
    void shouldIgnoreUnknownOrEmptyChunks() {
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        when(chunkMapper.selectByIds(anyCollection())).thenReturn(List.of());
        RetrievalContext context = RetrievalContext.builder()
                .intentChunks(Map.of(
                        "intent-1",
                        List.of(RetrievedChunk.builder().id("unknown").text("x").build())))
                .build();

        List<EvidenceItem> result = new RetrievalContextEvidenceAdapter(
                chunkMapper, documentMapper).adapt(context, Map.of(), 1);

        assertEquals(List.of(), result);
    }
}
