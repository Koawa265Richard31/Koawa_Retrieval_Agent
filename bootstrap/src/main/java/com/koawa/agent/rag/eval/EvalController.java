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

package com.koawa.agent.rag.eval;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.koawa.agent.framework.context.UserContext;
import com.koawa.agent.framework.convention.RetrievedChunk;
import com.koawa.agent.framework.exception.ClientException;
import com.koawa.agent.knowledge.dao.entity.KnowledgeChunkDO;
import com.koawa.agent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.koawa.agent.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.koawa.agent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.koawa.agent.rag.config.SearchChannelProperties;
import com.koawa.agent.rag.core.agentic.AgenticRetrievalOrchestrator;
import com.koawa.agent.rag.core.agentic.AgenticRetrievalResult;
import com.koawa.agent.rag.core.agentic.EvidenceCitation;
import com.koawa.agent.rag.core.agentic.EvidenceContextPresenter;
import com.koawa.agent.rag.core.agentic.RetrievalAccessPrincipal;
import com.koawa.agent.rag.core.agentic.RetrievalComplexityDecider;
import com.koawa.agent.rag.core.agentic.RetrievalComplexityDecision;
import com.koawa.agent.rag.core.agentic.RetrievalStopReason;
import com.koawa.agent.rag.core.intent.IntentResolver;
import com.koawa.agent.rag.core.retrieve.RetrievalEngine;
import com.koawa.agent.rag.core.rewrite.QueryRewriteService;
import com.koawa.agent.rag.core.rewrite.RewriteResult;
import com.koawa.agent.rag.dto.RetrievalContext;
import com.koawa.agent.rag.dto.SubQuestionIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.koawa.agent.framework.convention.Result;
import com.koawa.agent.framework.web.Results;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 效果评测接口
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.eval", name = "enabled", havingValue = "true")
public class EvalController {

    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final RetrievalEngine retrievalEngine;
    private final SearchChannelProperties searchProperties;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final RetrievalComplexityDecider complexityDecider;
    private final AgenticRetrievalOrchestrator agenticRetrievalOrchestrator;
    private final EvidenceContextPresenter evidenceContextPresenter;

    @GetMapping("/rag/eval")
    public Result<EvalResponse> chat(
            @RequestParam String question,
            @RequestParam(defaultValue = "single") String mode) {
        long start = System.currentTimeMillis();
        String evaluationMode = normalizeMode(mode);

        RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(question, List.of());
        List<SubQuestionIntent> subIntents = intentResolver.resolve(rewriteResult);
        RetrievalComplexityDecision complexity = complexityDecider.decide(rewriteResult, subIntents);
        RetrievalContext singlePass = retrievalEngine.retrieve(
                subIntents, searchProperties.getDefaultTopK());
        long initialLatencyMs = System.currentTimeMillis() - start;

        RetrievalContext selected = singlePass;
        AgenticRetrievalResult agenticResult = null;
        boolean fallback = false;
        if ("active".equals(evaluationMode)) {
            try {
                agenticResult = agenticRetrievalOrchestrator.execute(
                        "eval-" + UUID.randomUUID(),
                        subIntents,
                        singlePass,
                        searchProperties.getDefaultTopK(),
                        currentPrincipal());
                if (agenticResult == null || agenticResult.retrievalContext() == null
                        || isFailure(agenticResult.stopReason())) {
                    fallback = true;
                } else {
                    RetrievalContext presented = evidenceContextPresenter.present(agenticResult);
                    if (presented == null) {
                        fallback = true;
                    } else {
                        selected = presented;
                    }
                }
            } catch (RuntimeException exception) {
                fallback = true;
            }
        }

        return Results.success(buildResponse(
                selected, subIntents, evaluationMode, complexity, agenticResult,
                fallback, initialLatencyMs, System.currentTimeMillis() - start));
    }

    private EvalResponse buildResponse(
            RetrievalContext rc,
            List<SubQuestionIntent> subIntents,
            String evaluationMode,
            RetrievalComplexityDecision complexity,
            AgenticRetrievalResult agenticResult,
            boolean fallback,
            long initialLatencyMs,
            long latencyMs) {
        List<RetrievedChunk> uniqueChunks = flattenChunks(rc);
        List<String> chunkIds = uniqueChunks.stream()
                .map(RetrievedChunk::getId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        List<String> contexts = uniqueChunks.stream()
                .map(RetrievedChunk::getText)
                .collect(Collectors.toList());

        // chunk 维度的 docId 列表：与 contexts 一一对应、保留 null、不去重
        List<String> contextDocIds = resolveContextDocIds(uniqueChunks);
        // doc 维度的 docId 列表：保持原语义（按 chunk 顺序首次出现、过滤 null）
        List<String> docIds = dedupNonBlank(contextDocIds);

        return EvalResponse.builder()
                .retrievedDocIds(docIds)
                .retrievedChunkIds(chunkIds)
                .retrievedContexts(contexts)
                .retrievedContextDocIds(contextDocIds)
                .mcpContext(rc == null ? null : rc.getMcpContext())
                .hasMcp(rc != null && rc.hasMcp())
                .hasKb(rc != null && rc.hasKb())
                .subIntents(extractSubIntents(subIntents))
                .intentLeafIds(extractTopLeafIds(subIntents))
                .evaluationMode(evaluationMode)
                .wouldRouteAgentic(complexity.complex())
                .complexityScore(complexity.score())
                .complexityReasons(complexity.reasons())
                .agenticStopReason(agenticResult == null || agenticResult.stopReason() == null
                        ? null : agenticResult.stopReason().name())
                .agenticIterations(agenticResult == null ? null : agenticResult.iterationCount())
                .agenticSufficient(agenticResult == null ? null : agenticResult.sufficient())
                .agenticFallbackToSinglePass(fallback)
                .citationIds(extractCitations(rc, true))
                .citationChunkIds(extractCitations(rc, false))
                .conflictedTaskIds(rc == null || rc.getConflictedTaskIds() == null
                        ? Collections.emptyList() : rc.getConflictedTaskIds())
                .initialRetrievalLatencyMs(initialLatencyMs)
                .latencyMs(latencyMs)
                .build();
    }

    private String normalizeMode(String mode) {
        String normalized = StrUtil.blankToDefault(mode, "single").trim().toLowerCase();
        if (!"single".equals(normalized) && !"active".equals(normalized)) {
            throw new ClientException("评测模式仅支持 single 或 active");
        }
        return normalized;
    }

    private RetrievalAccessPrincipal currentPrincipal() {
        return new RetrievalAccessPrincipal(
                UserContext.getUserId(), UserContext.getUsername(), UserContext.getRole());
    }

    private boolean isFailure(RetrievalStopReason reason) {
        return reason == RetrievalStopReason.CANCELLED
                || reason == RetrievalStopReason.TIMEOUT
                || reason == RetrievalStopReason.PLANNING_FAILED
                || reason == RetrievalStopReason.RETRIEVAL_FAILED
                || reason == RetrievalStopReason.EVALUATION_FAILED;
    }

    private List<String> extractCitations(RetrievalContext rc, boolean ids) {
        if (rc == null || CollUtil.isEmpty(rc.getCitations())) {
            return Collections.emptyList();
        }
        return rc.getCitations().stream()
                .map(ids ? EvidenceCitation::citationId : EvidenceCitation::chunkId)
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    /**
     * 摊平 intentChunks（Map<intentId, List<RetrievedChunk>>），按 chunk id 去重并保留首次顺序
     */
    private List<RetrievedChunk> flattenChunks(RetrievalContext rc) {
        if (rc == null || CollUtil.isEmpty(rc.getIntentChunks())) {
            return Collections.emptyList();
        }
        Set<String> seen = new LinkedHashSet<>();
        return rc.getIntentChunks().values().stream()
                .filter(CollUtil::isNotEmpty)
                .flatMap(List::stream)
                .filter(c -> c != null && StrUtil.isNotBlank(c.getId()))
                .filter(c -> seen.add(c.getId()))
                .collect(Collectors.toList());
    }

    /**
     * 与 chunks 一一对应的业务 docId 列表（长度相同、保留 null、不去重）
     * 链路：chunkId → t_knowledge_chunk.docId（雪花）→ t_knowledge_document.doc_name → 剥文件后缀
     * 评测集的 reference_doc_ids 用业务码（如 `FAQ_VAC_001`），与此处对齐
     */
    private List<String> resolveContextDocIds(List<RetrievedChunk> chunks) {
        if (CollUtil.isEmpty(chunks)) {
            return Collections.emptyList();
        }
        List<String> chunkIdsForLookup = chunks.stream()
                .map(RetrievedChunk::getId)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (chunkIdsForLookup.isEmpty()) {
            return new java.util.ArrayList<>(Collections.nCopies(chunks.size(), null));
        }
        // 第一跳：chunkId → 雪花 docId
        Map<String, String> chunkIdToInternalDocId = knowledgeChunkMapper.selectByIds(chunkIdsForLookup).stream()
                .filter(c -> StrUtil.isNotBlank(c.getId()) && StrUtil.isNotBlank(c.getDocId()))
                .collect(Collectors.toMap(
                        KnowledgeChunkDO::getId,
                        KnowledgeChunkDO::getDocId,
                        (a, b) -> a));
        // 第二跳：雪花 docId → 业务码（doc_name 剥后缀）
        List<String> internalDocIds = chunkIdToInternalDocId.values().stream().distinct().collect(Collectors.toList());
        Map<String, String> internalToBizDocId = internalDocIds.isEmpty()
                ? Map.of()
                : knowledgeDocumentMapper.selectByIds(internalDocIds).stream()
                        .filter(d -> StrUtil.isNotBlank(d.getId()) && StrUtil.isNotBlank(d.getDocName()))
                        .collect(Collectors.toMap(
                                KnowledgeDocumentDO::getId,
                                d -> stripExtension(d.getDocName()),
                                (a, b) -> a));
        // 按 chunks 原顺序展开（null 占位保留）
        return chunks.stream()
                .map(c -> {
                    if (StrUtil.isBlank(c.getId())) {
                        return null;
                    }
                    String internal = chunkIdToInternalDocId.get(c.getId());
                    if (StrUtil.isBlank(internal)) {
                        return null;
                    }
                    return internalToBizDocId.get(internal);
                })
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    /**
     * 剥掉最后一个 `.` 之后的文件扩展名；无后缀则原样返回
     */
    private static String stripExtension(String docName) {
        if (docName == null) {
            return null;
        }
        int dot = docName.lastIndexOf('.');
        return (dot > 0 && dot < docName.length() - 1) ? docName.substring(0, dot) : docName;
    }

    /**
     * 按首次出现顺序去重并过滤空值
     */
    private List<String> dedupNonBlank(List<String> in) {
        if (CollUtil.isEmpty(in)) {
            return Collections.emptyList();
        }
        Set<String> seen = new LinkedHashSet<>();
        return in.stream()
                .filter(StrUtil::isNotBlank)
                .filter(seen::add)
                .collect(Collectors.toList());
    }

    private List<String> extractSubIntents(List<SubQuestionIntent> intents) {
        if (CollUtil.isEmpty(intents)) {
            return Collections.emptyList();
        }
        return intents.stream()
                .map(SubQuestionIntent::subQuestion)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }

    private List<String> extractTopLeafIds(List<SubQuestionIntent> intents) {
        if (CollUtil.isEmpty(intents)) {
            return Collections.emptyList();
        }
        return intents.stream()
                .map(si -> {
                    if (CollUtil.isEmpty(si.nodeScores())) {
                        return null;
                    }
                    return si.nodeScores().get(0).getNode().getId();
                })
                .collect(Collectors.toList());
    }
}
