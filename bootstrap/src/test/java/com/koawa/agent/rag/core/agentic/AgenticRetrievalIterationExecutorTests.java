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

package com.koawa.agent.rag.core.agentic;

import com.koawa.agent.rag.core.retrieve.RetrievalEngine;
import com.koawa.agent.rag.dto.RetrievalContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgenticRetrievalIterationExecutorTests {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void returnsRetrievalResultWithinDeadline() throws Exception {
        RetrievalEngine engine = mock(RetrievalEngine.class);
        RetrievalContext expected = RetrievalContext.builder().build();
        when(engine.retrieve(any(), anyInt())).thenReturn(expected);

        RetrievalContext result = new AgenticRetrievalIterationExecutor(engine, executor)
                .retrieve(List.of(), 5, Instant.now().plusSeconds(1), () -> false);

        assertSame(expected, result);
    }

    @Test
    void expiredDeadlineStopsWaiting() {
        RetrievalEngine engine = mock(RetrievalEngine.class);

        assertThrows(
                TimeoutException.class,
                () -> new AgenticRetrievalIterationExecutor(engine, executor)
                        .retrieve(List.of(), 5, Instant.now(), () -> false));
    }

    @Test
    void cancellationStopsWaiting() {
        RetrievalEngine engine = mock(RetrievalEngine.class);

        assertThrows(
                CancellationException.class,
                () -> new AgenticRetrievalIterationExecutor(engine, executor)
                        .retrieve(List.of(), 5, Instant.now().plusSeconds(1), () -> true));
    }
}
