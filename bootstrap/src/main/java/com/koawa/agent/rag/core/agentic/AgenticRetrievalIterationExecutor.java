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
import com.koawa.agent.rag.dto.SubQuestionIntent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

@Component
public class AgenticRetrievalIterationExecutor {

    private static final long CANCELLATION_POLL_MS = 100;

    private final RetrievalEngine retrievalEngine;
    private final ExecutorService executor;

    public AgenticRetrievalIterationExecutor(
            RetrievalEngine retrievalEngine,
            @Qualifier("agenticRetrievalIterationThreadPool") ExecutorService executor) {
        this.retrievalEngine = retrievalEngine;
        this.executor = executor;
    }

    public RetrievalContext retrieve(
            List<SubQuestionIntent> intents,
            int topK,
            Instant deadline,
            BooleanSupplier cancelled)
            throws TimeoutException, ExecutionException, InterruptedException {
        Future<RetrievalContext> future = executor.submit(
                () -> retrievalEngine.retrieve(intents, topK));
        try {
            while (true) {
                if (cancelled.getAsBoolean()) {
                    throw new CancellationException("retrieval was cancelled");
                }
                long remainingMs = Duration.between(Instant.now(), deadline).toMillis();
                if (remainingMs <= 0) {
                    throw new TimeoutException("agentic retrieval deadline exceeded");
                }
                try {
                    return future.get(
                            Math.min(remainingMs, CANCELLATION_POLL_MS),
                            TimeUnit.MILLISECONDS);
                } catch (TimeoutException exception) {
                    if (remainingMs <= CANCELLATION_POLL_MS) {
                        throw exception;
                    }
                }
            }
        } finally {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }
}
