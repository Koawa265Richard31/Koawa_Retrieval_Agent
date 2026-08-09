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
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimeWeightHelperTest {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");
    private static final long DAY = 86_400_000L;

    private final TimeWeightProperties props = new TimeWeightProperties();
    private final TimeWeightHelper helper = new TimeWeightHelper(props);

    private RetrievedChunk chunk(String id, String docId, Long sourceTime, float score) {
        return RetrievedChunk.builder().id(id).docId(docId).sourceTime(sourceTime).score(score).build();
    }

    @Test
    void decayIsOneAtZeroAge() {
        assertThat(helper.decay(NOW.toEpochMilli(), NOW)).isEqualTo(1.0);
    }

    @Test
    void decayIsHalfAtHalfLife() {
        double half = helper.decay(NOW.toEpochMilli() - props.getHalfLifeDays() * DAY, NOW);
        assertThat(half).isCloseTo(0.5, Offset.offset(1e-6));
    }

    @Test
    void decayDecreasesWithAge() {
        double fresh = helper.decay(NOW.toEpochMilli() - 10 * DAY, NOW);
        double old = helper.decay(NOW.toEpochMilli() - 300 * DAY, NOW);
        assertThat(fresh).isGreaterThan(old);
    }

    @Test
    void reorderBoostsNewerDocAheadOfOlderDocWhenScoresClose() {
        RetrievedChunk oldChunk = chunk("old", "doc-old", NOW.toEpochMilli() - 400 * DAY, 0.90f);
        RetrievedChunk newChunk = chunk("new", "doc-new", NOW.toEpochMilli() - 5 * DAY, 0.88f);
        List<RetrievedChunk> out = helper.reorder(List.of(oldChunk, newChunk), NOW, 10, 0);
        assertThat(out).extracting(RetrievedChunk::getId).containsExactly("new", "old");
    }

    @Test
    void reorderKeepsScoreOrderWhenNoTime() {
        RetrievedChunk a = chunk("a", "d1", null, 0.9f);
        RetrievedChunk b = chunk("b", "d2", null, 0.8f);
        List<RetrievedChunk> out = helper.reorder(List.of(a, b), NOW, 10, 0);
        assertThat(out).extracting(RetrievedChunk::getId).containsExactly("a", "b");
    }

    @Test
    void reorderLimitsChunksPerDoc() {
        long t = NOW.toEpochMilli() - 10 * DAY;
        List<RetrievedChunk> chunks = List.of(
                chunk("a1", "docA", t, 0.90f),
                chunk("a2", "docA", t, 0.89f),
                chunk("a3", "docA", t, 0.88f),
                chunk("a4", "docA", t, 0.87f)
        );
        List<RetrievedChunk> out = helper.reorder(chunks, NOW, 10, 3);
        assertThat(out).hasSize(3);
        assertThat(out).allMatch(c -> "docA".equals(c.getDocId()));
    }

    @Test
    void reorderAppliesLimit() {
        long t = NOW.toEpochMilli() - 10 * DAY;
        List<RetrievedChunk> chunks = List.of(
                chunk("a", "d1", t, 0.9f),
                chunk("b", "d2", t, 0.8f),
                chunk("c", "d3", t, 0.7f)
        );
        List<RetrievedChunk> out = helper.reorder(chunks, NOW, 2, 0);
        assertThat(out).hasSize(2);
    }
}
