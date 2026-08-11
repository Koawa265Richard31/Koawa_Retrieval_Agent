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

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 博查 AI 搜索（主路）：国内访问稳定、中文社区（B站/NGA/贴吧）收录好，支持多语言。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BochaSearchProvider implements SearchProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Qualifier("syncHttpClient")
    private final OkHttpClient httpClient;
    private final WebSearchProperties properties;

    @Override
    public String providerName() {
        return "bocha";
    }

    @Override
    public boolean isAvailable() {
        return StrUtil.isNotBlank(properties.getBocha().getApiKey());
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (!isAvailable()) {
            return List.of();
        }
        JSONObject body = new JSONObject();
        body.set("query", query);
        body.set("summary", true);
        body.set("count", maxResults);
        body.set("freshness", "noLimit");
        try {
            Request request = new Request.Builder()
                    .url(properties.getBocha().getEndpoint())
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + properties.getBocha().getApiKey())
                    .header("Content-Type", "application/json")
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("[web-search] 博查请求失败 status={}", response.code());
                    return List.of();
                }
                String text = response.body().string();
                JSONObject root = JSONUtil.parseObj(text);
                JSONArray values = root.getByPath("data.webPages.value", JSONArray.class);
                if (values == null) {
                    return List.of();
                }
                List<SearchResult> results = new ArrayList<>();
                for (Object item : values) {
                    JSONObject it = (JSONObject) item;
                    results.add(SearchResult.builder()
                            .provider(providerName())
                            .title(it.getStr("name"))
                            .url(it.getStr("url"))
                            .snippet(it.getStr("snippet"))
                            .description(StrUtil.blankToDefault(it.getStr("summary"), it.getStr("snippet")))
                            .resourceCreateTime(parseTime(it.getStr("datePublished")))
                            .build());
                }
                return results;
            }
        } catch (Exception e) {
            log.warn("[web-search] 博查搜索异常 query={}", query, e);
            return List.of();
        }
    }

    private Instant parseTime(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // fallthrough
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
