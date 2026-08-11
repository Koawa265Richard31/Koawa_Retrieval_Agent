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

import java.util.List;

/**
 * 联网搜索提供方（可插拔：博查 / Bing / 未来可扩展）
 */
public interface SearchProvider {

    /**
     * 提供方标识：bocha / bing
     */
    String providerName();

    /**
     * 是否可用（apiKey 已配置）
     */
    boolean isAvailable();

    /**
     * 执行搜索
     *
     * @param query      检索查询（整句改写查询，非关键词）
     * @param maxResults 最多返回条数
     */
    List<SearchResult> search(String query, int maxResults);
}
