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

package com.koawa.agent.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.koawa.agent.framework.context.UserContext;
import com.koawa.agent.framework.exception.ClientException;
import com.koawa.agent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.koawa.agent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.koawa.agent.rag.controller.request.MessageFeedbackPageRequest;
import com.koawa.agent.rag.controller.vo.MessageFeedbackCategoryStatVO;
import com.koawa.agent.rag.controller.vo.MessageFeedbackGovernanceVO;
import com.koawa.agent.rag.controller.vo.MessageFeedbackVO;
import com.koawa.agent.rag.dao.entity.MessageFeedbackDO;
import com.koawa.agent.rag.dao.entity.RagTraceNodeDO;
import com.koawa.agent.rag.dao.mapper.MessageFeedbackMapper;
import com.koawa.agent.rag.dao.mapper.RagTraceNodeMapper;
import com.koawa.agent.rag.service.MessageFeedbackAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 消息反馈管理服务实现
 */
@Service
@RequiredArgsConstructor
public class MessageFeedbackAdminServiceImpl implements MessageFeedbackAdminService {

    private final MessageFeedbackMapper feedbackMapper;
    private final RagTraceNodeMapper traceNodeMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Override
    public IPage<MessageFeedbackVO> pageQuery(MessageFeedbackPageRequest request) {
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        Page<?> page = new Page<>(request.getCurrent(), request.getSize());
        return feedbackMapper.pageFeedback(
                page,
                request.getVote(),
                request.getHandled(),
                request.getRating(),
                StrUtil.trimToNull(request.getReason()),
                StrUtil.trimToNull(request.getKeyword())
        );
    }

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> result = new HashMap<>(8);
        long total = feedbackMapper.selectCount(
                Wrappers.lambdaQuery(MessageFeedbackDO.class).eq(MessageFeedbackDO::getDeleted, 0));
        long likeCount = feedbackMapper.selectCount(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getDeleted, 0)
                        .eq(MessageFeedbackDO::getVote, 1));
        long dislikeCount = feedbackMapper.selectCount(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getDeleted, 0)
                        .eq(MessageFeedbackDO::getVote, -1));
        long unhandledCount = feedbackMapper.selectCount(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getDeleted, 0)
                        .eq(MessageFeedbackDO::getHandled, 0));
        long handledCount = feedbackMapper.selectCount(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getDeleted, 0)
                        .eq(MessageFeedbackDO::getHandled, 1));
        long todayCount = feedbackMapper.selectCount(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getDeleted, 0)
                        .ge(MessageFeedbackDO::getCreateTime, DateUtil.beginOfDay(new Date())));

        // 满意度星级统计
        List<Map<String, Object>> ratingRows = feedbackMapper.selectMaps(
                Wrappers.query(MessageFeedbackDO.class)
                        .select("ROUND(AVG(rating)::numeric, 2) AS avg_rating",
                                "COUNT(rating) AS rated_count",
                                "COUNT(*) FILTER (WHERE rating IS NOT NULL AND rating < 4) AS low_rating_count",
                                "COUNT(*) FILTER (WHERE rating = 1) AS r1",
                                "COUNT(*) FILTER (WHERE rating = 2) AS r2",
                                "COUNT(*) FILTER (WHERE rating = 3) AS r3",
                                "COUNT(*) FILTER (WHERE rating = 4) AS r4",
                                "COUNT(*) FILTER (WHERE rating = 5) AS r5")
                        .eq("deleted", 0));
        if (CollUtil.isNotEmpty(ratingRows)) {
            Map<String, Object> row = ratingRows.get(0);
            result.put("avgRating", row.get("avg_rating"));
            result.put("ratedCount", row.get("rated_count"));
            result.put("lowRatingCount", row.get("low_rating_count"));
            Map<String, Object> dist = new LinkedHashMap<>();
            dist.put("1", row.get("r1"));
            dist.put("2", row.get("r2"));
            dist.put("3", row.get("r3"));
            dist.put("4", row.get("r4"));
            dist.put("5", row.get("r5"));
            result.put("ratingDistribution", dist);
        }
        result.put("total", total);
        result.put("likeCount", likeCount);
        result.put("dislikeCount", dislikeCount);
        result.put("unhandledCount", unhandledCount);
        result.put("handledCount", handledCount);
        result.put("todayCount", todayCount);
        return result;
    }

    @Override
    public List<MessageFeedbackCategoryStatVO> categoryStats() {
        return feedbackMapper.selectCategoryStats();
    }

    @Override
    public List<MessageFeedbackGovernanceVO> governance(Integer handled) {
        List<MessageFeedbackVO> rows = feedbackMapper.selectGovernanceFeedback(handled);
        if (CollUtil.isEmpty(rows)) {
            return List.of();
        }
        Map<String, List<MessageFeedbackVO>> feedbackByTrace = rows.stream()
                .filter(row -> StrUtil.isNotBlank(row.getTraceId()))
                .collect(Collectors.groupingBy(MessageFeedbackVO::getTraceId));
        if (feedbackByTrace.isEmpty()) {
            return List.of();
        }
        Map<String, Set<String>> docIdsByTrace = loadTraceDocIds(new ArrayList<>(feedbackByTrace.keySet()));
        if (docIdsByTrace.isEmpty()) {
            return List.of();
        }
        Map<String, List<MessageFeedbackVO>> feedbackByDoc = new LinkedHashMap<>();
        for (MessageFeedbackVO row : rows) {
            Set<String> docIds = docIdsByTrace.get(row.getTraceId());
            if (CollUtil.isEmpty(docIds)) {
                continue;
            }
            for (String docId : docIds) {
                feedbackByDoc.computeIfAbsent(docId, key -> new ArrayList<>()).add(row);
            }
        }
        if (feedbackByDoc.isEmpty()) {
            return List.of();
        }
        Map<String, KnowledgeDocumentDO> docMap = loadDocMap(feedbackByDoc.keySet());
        List<MessageFeedbackGovernanceVO> result = new ArrayList<>();
        for (Map.Entry<String, List<MessageFeedbackVO>> entry : feedbackByDoc.entrySet()) {
            String docId = entry.getKey();
            List<MessageFeedbackVO> fbList = entry.getValue();
            KnowledgeDocumentDO doc = docMap.get(docId);
            long unhandled = fbList.stream().filter(r -> r.getHandled() != null && r.getHandled() == 0).count();
            Date recent = fbList.stream()
                    .map(MessageFeedbackVO::getCreateTime)
                    .filter(Objects::nonNull)
                    .max(Date::compareTo)
                    .orElse(null);
            List<String> questions = fbList.stream()
                    .map(MessageFeedbackVO::getQuestion)
                    .filter(StrUtil::isNotBlank)
                    .distinct()
                    .limit(3)
                    .collect(Collectors.toList());
            String contentId = doc == null ? null : resolveContentId(doc.getDocName());
            result.add(MessageFeedbackGovernanceVO.builder()
                    .docId(docId)
                    .docName(doc == null ? docId : doc.getDocName())
                    .kbId(doc == null ? null : doc.getKbId())
                    .sourceType(doc == null ? null : doc.getSourceType())
                    .sourceLocation(doc == null ? null : doc.getSourceLocation())
                    .contentId(contentId)
                    .reCrawlable(contentId != null && !contentId.startsWith("9000"))
                    .dislikeCount((long) fbList.size())
                    .unhandledCount(unhandled)
                    .recentTime(recent)
                    .sampleQuestions(questions)
                    .build());
        }
        result.sort(Comparator.comparing(MessageFeedbackGovernanceVO::getDislikeCount, Comparator.reverseOrder())
                .thenComparing(MessageFeedbackGovernanceVO::getRecentTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private Map<String, Set<String>> loadTraceDocIds(List<String> traceIds) {
        Map<String, Set<String>> result = new HashMap<>();
        if (CollUtil.isEmpty(traceIds)) {
            return result;
        }
        List<RagTraceNodeDO> nodes = traceNodeMapper.selectList(
                Wrappers.lambdaQuery(RagTraceNodeDO.class)
                        .eq(RagTraceNodeDO::getDeleted, 0)
                        .eq(RagTraceNodeDO::getNodeType, "RETRIEVE")
                        .in(RagTraceNodeDO::getTraceId, traceIds)
                        .isNotNull(RagTraceNodeDO::getExtraData));
        for (RagTraceNodeDO node : nodes) {
            Set<String> docIds = parseDocIds(node.getExtraData());
            if (CollUtil.isNotEmpty(docIds)) {
                result.computeIfAbsent(node.getTraceId(), key -> new HashSet<>()).addAll(docIds);
            }
        }
        return result;
    }

    private Set<String> parseDocIds(String extraData) {
        if (StrUtil.isBlank(extraData)) {
            return Set.of();
        }
        try {
            JSONArray array = JSONUtil.parseObj(extraData).getJSONArray("docIds");
            if (array == null) {
                return Set.of();
            }
            return array.stream()
                    .map(String::valueOf)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return Set.of();
        }
    }

    /**
     * 从文档名解析源内容ID：命名规范 {index}-{contentId}-{title}.md
     */
    private String resolveContentId(String docName) {
        if (StrUtil.isBlank(docName)) {
            return null;
        }
        Matcher matcher = Pattern.compile("^\\d+-(\\d+)-").matcher(docName.trim());
        return matcher.find() ? matcher.group(1) : null;
    }
    private Map<String, KnowledgeDocumentDO> loadDocMap(Set<String> docIds) {
        if (CollUtil.isEmpty(docIds)) {
            return Map.of();
        }
        List<KnowledgeDocumentDO> docs = knowledgeDocumentMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .eq(KnowledgeDocumentDO::getDeleted, 0)
                        .in(KnowledgeDocumentDO::getId, docIds));
        return docs.stream().collect(Collectors.toMap(KnowledgeDocumentDO::getId, doc -> doc, (a, b) -> a));
    }

    @Override
    public void handle(String id, String note) {
        MessageFeedbackDO record = loadById(id);
        String handlerId = UserContext.getUserId();
        feedbackMapper.update(null, Wrappers.lambdaUpdate(MessageFeedbackDO.class)
                .eq(MessageFeedbackDO::getId, record.getId())
                .set(MessageFeedbackDO::getHandled, 1)
                .set(MessageFeedbackDO::getHandleNote, StrUtil.trimToNull(note))
                .set(MessageFeedbackDO::getHandleTime, new Date())
                .set(MessageFeedbackDO::getHandlerId, handlerId));
    }

    @Override
    public void unhandle(String id) {
        MessageFeedbackDO record = loadById(id);
        feedbackMapper.update(null, Wrappers.lambdaUpdate(MessageFeedbackDO.class)
                .eq(MessageFeedbackDO::getId, record.getId())
                .set(MessageFeedbackDO::getHandled, 0)
                .set(MessageFeedbackDO::getHandleNote, null)
                .set(MessageFeedbackDO::getHandleTime, null)
                .set(MessageFeedbackDO::getHandlerId, null));
    }

    private MessageFeedbackDO loadById(String id) {
        MessageFeedbackDO record = feedbackMapper.selectOne(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getId, id)
                        .eq(MessageFeedbackDO::getDeleted, 0)
        );
        Assert.notNull(record, () -> new ClientException("反馈记录不存在"));
        return record;
    }
}





