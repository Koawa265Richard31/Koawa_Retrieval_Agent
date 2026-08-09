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
import com.koawa.agent.rag.core.retrieve.channel.SearchChannelResult;
import com.koawa.agent.rag.core.retrieve.channel.SearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 时间加权后置处理器（Rerank 前，候选池塑形）
 * <p>
 * 在去重之后、模型 Rerank 之前执行：把时间新鲜度混入向量得分，
 * 让最新时效的攻略/档案进入 Rerank 候选池，并限制单篇文档块数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeBoostPostProcessor implements SearchResultPostProcessor {

    private final TimeWeightHelper timeWeightHelper;
    private final TimeWeightProperties properties;

    @Override
    public String getName() {
        return "TimeBoost";
    }

    @Override
    public int getOrder() {
        return 5; // 去重(1) 之后，Rerank(10) 之前
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return properties.isEnabled();
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        if (!timeWeightHelper.anyHasTime(chunks)) {
            log.info("候选块均无 source_time，跳过时间加权（存量数据请先回填）");
            return chunks;
        }
        int limit = context.getTopK() * Math.max(1, properties.getRerankCandidateMultiplier());
        List<RetrievedChunk> reordered = timeWeightHelper.reorder(chunks, Instant.now(), limit, properties.getMaxChunksPerDoc());
        log.info("时间加权（Rerank 前）完成 - 输入: {}, 输出: {}", chunks.size(), reordered.size());
        return reordered;
    }
}
