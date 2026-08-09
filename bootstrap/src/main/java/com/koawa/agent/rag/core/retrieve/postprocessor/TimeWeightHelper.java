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

package com.koawa.agent.rag.core.retrieve.postprocessor;

import com.koawa.agent.framework.convention.RetrievedChunk;
import com.koawa.agent.rag.config.TimeWeightProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 时间新鲜度计算与排序工具（KB 时效治理）
 */
@Component
@RequiredArgsConstructor
public class TimeWeightHelper {

    private final TimeWeightProperties properties;

    /**
     * 新鲜度衰减值：age 超过半衰期后衰减到 0.5，指数衰减 0..1
     */
    public double decay(long sourceTimeMs, Instant now) {
        double ageDays = Math.max(0, Duration.between(Instant.ofEpochMilli(sourceTimeMs), now).toMillis() / 86_400_000.0);
        return Math.exp(-Math.log(2) * ageDays / Math.max(1, properties.getHalfLifeDays()));
    }

    public boolean anyHasTime(List<RetrievedChunk> chunks) {
        return chunks.stream().anyMatch(c -> c.getSourceTime() != null);
    }

    /**
     * 混合相似度得分与时间新鲜度，并做文档级数量限制后排序截断。
     * 得分 = 相似度 * (1 - λ) + 归一化新鲜度 * λ
     *
     * @param chunks     候选块（会被原地改写 score）
     * @param now        当前时间
     * @param limit      返回数量上限
     * @param maxPerDoc  每个文档最多保留的块数（0 不限制）
     */
    public List<RetrievedChunk> reorder(List<RetrievedChunk> chunks, Instant now, int limit, int maxPerDoc) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        double minD = Double.MAX_VALUE;
        double maxD = -Double.MAX_VALUE;
        for (RetrievedChunk c : chunks) {
            if (c.getSourceTime() == null) {
                continue;
            }
            double d = decay(c.getSourceTime(), now);
            minD = Math.min(minD, d);
            maxD = Math.max(maxD, d);
        }
        double span = maxD - minD;
        List<RetrievedChunk> boosted = new ArrayList<>(chunks.size());
        for (RetrievedChunk c : chunks) {
            if (c.getSourceTime() != null) {
                double d = decay(c.getSourceTime(), now);
                double dNorm = span > 0 ? (d - minD) / span : 1.0;
                double base = c.getScore() == null ? 0.5 : c.getScore();
                double blended = base * (1 - properties.getWeight()) + dNorm * properties.getWeight();
                c.setScore((float) blended);
            }
            boosted.add(c);
        }
        boosted.sort(Comparator.comparing(RetrievedChunk::getScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<RetrievedChunk> capped = new ArrayList<>(boosted.size());
        Map<String, Integer> docCount = new HashMap<>();
        for (RetrievedChunk c : boosted) {
            if (maxPerDoc > 0 && c.getDocId() != null) {
                int n = docCount.merge(c.getDocId(), 1, Integer::sum);
                if (n > maxPerDoc) {
                    continue;
                }
            }
            capped.add(c);
        }
        return capped.stream().limit(limit).collect(Collectors.toList());
    }
}
