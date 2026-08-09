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
 * 时间加权后置处理器（最终排序）
 * <p>
 * 在模型 Rerank 之后执行：把时间新鲜度与 Rerank/向量得分混合，
 * 作为最终 Top-K 的排序依据，并做文档级数量限制。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeBoostFinalPostProcessor implements SearchResultPostProcessor {

    private final TimeWeightHelper timeWeightHelper;
    private final TimeWeightProperties properties;

    @Override
    public String getName() {
        return "TimeBoostFinal";
    }

    @Override
    public int getOrder() {
        return 20; // Rerank(10) 之后，作为最终排序
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
            return chunks;
        }
        List<RetrievedChunk> reordered = timeWeightHelper.reorder(chunks, Instant.now(), context.getTopK(), properties.getMaxChunksPerDoc());
        log.info("时间加权（最终排序）完成 - 输入: {}, 输出: {}", chunks.size(), reordered.size());
        return reordered;
    }
}
