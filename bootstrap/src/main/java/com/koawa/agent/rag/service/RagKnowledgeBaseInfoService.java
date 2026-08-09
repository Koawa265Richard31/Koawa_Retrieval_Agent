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

package com.koawa.agent.rag.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.koawa.agent.knowledge.dao.entity.KnowledgeBaseDO;
import com.koawa.agent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.koawa.agent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 知识库元信息服务
 * <p>
 * 提供「知识库最后更新时间」的解析：指定 collectionName 时取该知识库
 * 未删除文档的最大 update_time；未指定时取全库文档的最大 update_time
 * （当前部署为单一学园偶像大师知识库产品，全库最大即为产品最后更新）。
 * 结果带 60s 本地缓存，避免每次对话都查询数据库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagKnowledgeBaseInfoService {

    private static final long CACHE_TTL_MS = 60_000L;
    private static final String GLOBAL_KEY = "__all__";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastCacheSweep = new AtomicLong(0);

    /**
     * 解析知识库最后更新时间（yyyy-MM-dd）。collectionName 为空时使用全库
     * 文档的最大更新时间。无数据返回 null。
     */
    public String resolveLastUpdatedAt(String collectionName) {
        String cacheKey = GLOBAL_KEY;
        if (StrUtil.isNotBlank(collectionName)) {
            String kbId = resolveKbId(collectionName.trim());
            if (StrUtil.isBlank(kbId)) {
                return null;
            }
            cacheKey = "kb:" + kbId;
        }
        CacheEntry entry = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (entry != null && now - entry.createdAt < CACHE_TTL_MS) {
            return entry.value;
        }
        try {
            Date maxUpdate = StrUtil.isNotBlank(collectionName)
                    ? knowledgeDocumentMapper.selectMaxUpdateTime(cacheKey.substring(3))
                    : knowledgeDocumentMapper.selectGlobalMaxUpdateTime();
            String value = null;
            if (maxUpdate != null) {
                value = new SimpleDateFormat("yyyy-MM-dd").format(maxUpdate);
            }
            cache.put(cacheKey, new CacheEntry(value, now));
            sweepCache(now);
            return value;
        } catch (Exception e) {
            log.warn("解析知识库最后更新时间失败 cacheKey={}", cacheKey, e);
            return null;
        }
    }

    private String resolveKbId(String collectionName) {
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                        .eq(KnowledgeBaseDO::getCollectionName, collectionName)
                        .eq(KnowledgeBaseDO::getDeleted, 0)
                        .last("LIMIT 1"));
        return kb == null ? null : kb.getId();
    }

    private void sweepCache(long now) {
        long last = lastCacheSweep.get();
        if (now - last < CACHE_TTL_MS) {
            return;
        }
        if (lastCacheSweep.compareAndSet(last, now)) {
            cache.entrySet().removeIf(e -> now - e.getValue().createdAt >= CACHE_TTL_MS);
        }
    }

    private record CacheEntry(String value, long createdAt) {
    }
}
