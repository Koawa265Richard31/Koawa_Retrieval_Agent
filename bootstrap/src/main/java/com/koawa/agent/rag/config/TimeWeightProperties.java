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

package com.koawa.agent.rag.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 检索时间加权配置（KB 时效治理）
 * <p>
 * 召回时在相似度得分上叠加时间新鲜度权重，让最新时效的攻略/档案优先被召回。
 */
@Data
@Component
public class TimeWeightProperties {

    /** 是否启用时间加权 */
    @Value("${rag.time-weight.enabled:true}")
    private boolean enabled = true;

    /** 时间权重 λ：最终得分 = 相似度 * (1 - λ) + 新鲜度 * λ */
    @Value("${rag.time-weight.weight:0.25}")
    private double weight = 0.25;

    /** 新鲜度半衰期（天）：超过该时长新鲜度衰减到 0.5 */
    @Value("${rag.time-weight.half-life-days:180}")
    private int halfLifeDays = 180;

    /** 每个文档最多进入最终 Top-K 的块数（防止单篇旧文档霸榜），0 表示不限制 */
    @Value("${rag.time-weight.max-chunks-per-doc:3}")
    private int maxChunksPerDoc = 3;

    /** Rerank 候选池放大倍数（时间加权后截断到 topK * 该倍数再交给 Rerank） */
    @Value("${rag.time-weight.rerank-candidate-multiplier:3}")
    private int rerankCandidateMultiplier = 3;
}
