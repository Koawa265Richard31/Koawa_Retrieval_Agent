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

package com.koawa.agent.rag.websearch;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 复合搜索提供方：按顺序调用可用提供方（博查 → Bing），按 URL 去重合并。
 * 总开关 enabled=false 或全部不可用时返回空（不联网）。
 */
@Component
@RequiredArgsConstructor
public class CompositeSearchProvider {

    private final List<SearchProvider> providers;
    private final WebSearchProperties properties;

    /**
     * 执行联网搜索（双路合并去重）
     *
     * @param query 检索查询
     * @return 合并去重后的结果列表
     */
    public List<SearchResult> search(String query) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        List<SearchResult> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (SearchProvider provider : providers) {
            if (!provider.isAvailable()) {
                continue;
            }
            for (SearchResult result : provider.search(query, properties.getMaxResults())) {
                if (result == null || result.getUrl() == null || !seen.add(result.getUrl())) {
                    continue;
                }
                merged.add(result);
            }
        }
        return merged;
    }
}
