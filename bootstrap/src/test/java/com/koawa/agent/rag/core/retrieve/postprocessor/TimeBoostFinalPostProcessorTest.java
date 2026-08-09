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
import com.koawa.agent.rag.core.retrieve.channel.SearchContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimeBoostFinalPostProcessorTest {

    private static final long NOW = Instant.parse("2026-08-09T00:00:00Z").toEpochMilli();

    private final TimeWeightProperties props = new TimeWeightProperties();
    private final TimeWeightHelper helper = new TimeWeightHelper(props);
    private final TimeBoostFinalPostProcessor processor = new TimeBoostFinalPostProcessor(helper, props);

    private List<RetrievedChunk> chunksWithTime(int n, long ageDays) {
        List<RetrievedChunk> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(RetrievedChunk.builder()
                    .id("c" + i)
                    .docId("d" + (i % 3))
                    .sourceTime(NOW - ageDays * 86_400_000L)
                    .score(0.9f - i * 0.01f)
                    .build());
        }
        return list;
    }

    @Test
    void processTruncatesToTopK() {
        SearchContext ctx = SearchContext.builder().topK(4).build();
        List<RetrievedChunk> out = processor.process(chunksWithTime(15, 10), List.of(), ctx);
        assertThat(out).hasSize(4);
    }

    @Test
    void processReturnsSameListWhenNoTime() {
        SearchContext ctx = SearchContext.builder().topK(3).build();
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().id("x").score(0.8f).build());
        List<RetrievedChunk> out = processor.process(chunks, List.of(), ctx);
        assertThat(out).isSameAs(chunks);
    }
}
