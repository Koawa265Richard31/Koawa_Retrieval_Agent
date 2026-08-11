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

package com.koawa.agent.agent.executor.handler;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.koawa.agent.agent.domain.AgentAction;
import com.koawa.agent.agent.domain.AgentActionType;
import com.koawa.agent.agent.domain.AgentObservation;
import com.koawa.agent.agent.domain.AgentState;
import com.koawa.agent.agent.executor.AgentActionHandler;
import com.koawa.agent.framework.trace.RagTraceContext;
import com.koawa.agent.rag.dao.entity.RagTraceNodeDO;
import com.koawa.agent.rag.dao.entity.WebSearchRecordDO;
import com.koawa.agent.rag.service.RagTraceRecordService;
import com.koawa.agent.rag.service.WebSearchRecordService;
import com.koawa.agent.rag.websearch.CompositeSearchProvider;
import com.koawa.agent.rag.websearch.SearchResult;
import com.koawa.agent.rag.websearch.WebSearchProperties;
import lombok.RequiredArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 联网搜索 Action Handler
 * <p>
 * 软要求触发：仅当 planner 判定知识库信息不足/时效性需求时选择本 Action；
 * 本 Handler 额外防御：
 * - 总开关未开启时拒绝；
 * - 查询为纯 URL（用户指定网址）时拒绝，避免访问不合法网站与浪费搜索额度；
 * - 查询过长截断。
 * 每次搜索会：写 WEB_SEARCH trace 节点（查询/结果/访问时间/资源创建时间），
 * 并把访问过的网址与原始问题绑定写入 t_web_search_record。
 */
@RequiredArgsConstructor
public class WebSearchActionHandler implements AgentActionHandler {

    private static final String NODE_TYPE = "WEB_SEARCH";
    private static final String NODE_NAME = "agent-web-search";

    private final CompositeSearchProvider searchProvider;
    private final WebSearchProperties properties;
    private final WebSearchRecordService recordService;
    private final RagTraceRecordService traceRecordService;

    @Override
    public AgentActionType supportedAction() {
        return AgentActionType.WEB_SEARCH;
    }

    @Override
    public AgentObservation execute(AgentAction action, AgentState state) {
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        if (action.getType() != AgentActionType.WEB_SEARCH) {
            throw new IllegalArgumentException("Unsupported action type: " + action.getType());
        }

        if (!properties.isEnabled()) {
            return failed("联网搜索未启用");
        }

        String rawQuery = resolveQuery(action);
        String query = rawQuery.trim();
        if (query.isBlank()) {
            return failed("WEB_SEARCH query 不能为空");
        }
        if (query.toLowerCase().startsWith("http://") || query.toLowerCase().startsWith("https://")) {
            return failed("不直接搜索指定网址，请提供检索查询（用户指定网址可直接自行访问或由其他工具总结）");
        }
        if (query.length() > 200) {
            query = query.substring(0, 200);
        }

        String traceId = RagTraceContext.getTraceId();
        String nodeId = IdUtil.getSnowflakeNextIdStr();
        long startMillis = System.currentTimeMillis();
        if (traceId != null) {
            traceRecordService.startNode(RagTraceNodeDO.builder()
                    .traceId(traceId)
                    .nodeId(nodeId)
                    .depth(RagTraceContext.depth())
                    .nodeType(NODE_TYPE)
                    .nodeName(NODE_NAME)
                    .status("RUNNING")
                    .startTime(new Date())
                    .build());
        }

        try {
            List<SearchResult> results = searchProvider.search(query);
            if (results.isEmpty()) {
                finishNode(traceId, nodeId, startMillis, false, null, query, List.of());
                return failed("联网未获取到有效结果，请确认查询或稍后重试");
            }

            // 记录访问过的网址（与原始问题绑定）
            Date visitTime = new Date();
            for (SearchResult result : results) {
                recordService.record(WebSearchRecordDO.builder()
                        .traceId(traceId)
                        .conversationId(state.getConversationId())
                        .messageId(null)
                        .question(state.getOriginalQuestion())
                        .provider(result.getProvider())
                        .query(query)
                        .url(result.getUrl())
                        .urlTitle(result.getTitle())
                        .description(result.getDescription())
                        .snippet(result.getSnippet())
                        .visitTime(visitTime)
                        .resourceCreateTime(result.getResourceCreateTime() == null ? null : Date.from(result.getResourceCreateTime()))
                        .build());
            }

            String content = buildContent(query, results);
            finishNode(traceId, nodeId, startMillis, true, null, query, results);
            return AgentObservation.builder()
                    .actionType(AgentActionType.WEB_SEARCH)
                    .success(true)
                    .content(content)
                    .metadata(Map.of(
                            "query", query,
                            "resultCount", results.size(),
                            "urls", results.stream().map(SearchResult::getUrl).toList()
                    ))
                    .build();
        } catch (Exception e) {
            finishNode(traceId, nodeId, startMillis, false, e.getMessage(), query, List.of());
            return failed("联网搜索异常: " + e.getMessage());
        }
    }

    private String resolveQuery(AgentAction action) {
        Map<String, Object> arguments = action.getArguments();
        if (arguments == null || arguments.get("query") == null) {
            throw new IllegalArgumentException("WEB_SEARCH query 不能为空");
        }
        return String.valueOf(arguments.get("query"));
    }

    private AgentObservation failed(String message) {
        return AgentObservation.builder()
                .actionType(AgentActionType.WEB_SEARCH)
                .success(false)
                .errorMessage(message)
                .content(message)
                .build();
    }

    private String buildContent(String query, List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("联网搜索结果（查询：").append(query).append("）：\n");
        int i = 1;
        for (SearchResult r : results) {
            sb.append(i++).append(". ");
            if (StrUtil.isNotBlank(r.getTitle())) {
                sb.append(r.getTitle());
            } else {
                sb.append(r.getUrl());
            }
            sb.append("（来源：").append(r.getProvider());
            if (r.getResourceCreateTime() != null) {
                sb.append("；发布时间：").append(r.getResourceCreateTime().toString().substring(0, 10));
            }
            sb.append("）\n");
            sb.append("   链接：").append(r.getUrl()).append("\n");
            if (StrUtil.isNotBlank(r.getDescription())) {
                sb.append("   摘要：").append(r.getDescription()).append("\n");
            }
        }
        return sb.toString();
    }

    private void finishNode(String traceId, String nodeId, long startMillis,
                            boolean success, String error, String query, List<SearchResult> results) {
        if (traceId == null || nodeId == null) {
            return;
        }
        long durationMs = Math.max(0, System.currentTimeMillis() - startMillis);
        try {
            traceRecordService.finishNode(traceId, nodeId,
                    success ? "SUCCESS" : "ERROR",
                    error, new Date(), durationMs);
            if (success) {
                JSONObject extra = new JSONObject();
                extra.set("query", query);
                JSONArray arr = new JSONArray();
                for (SearchResult r : results) {
                    JSONObject o = new JSONObject();
                    o.set("provider", r.getProvider());
                    o.set("url", r.getUrl());
                    o.set("title", r.getTitle());
                    o.set("description", r.getDescription());
                    o.set("visitTime", String.valueOf(System.currentTimeMillis()));
                    o.set("resourceCreateTime", r.getResourceCreateTime() == null ? null : r.getResourceCreateTime().toString());
                    arr.add(o);
                }
                extra.set("results", arr);
                traceRecordService.updateNodeExtra(traceId, nodeId, extra.toString());
            }
        } catch (Exception ignored) {
            // trace 写入失败不影响检索主流程
        }
    }
}
