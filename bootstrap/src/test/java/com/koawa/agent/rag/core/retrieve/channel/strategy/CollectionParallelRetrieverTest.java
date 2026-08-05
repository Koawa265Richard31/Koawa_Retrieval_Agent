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

package com.koawa.agent.rag.core.retrieve.channel.strategy;

import com.koawa.agent.framework.convention.RetrievedChunk;
import com.koawa.agent.framework.exception.RemoteException;
import com.koawa.agent.rag.core.retrieve.RetrieveRequest;
import com.koawa.agent.rag.core.retrieve.RetrieverService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionParallelRetrieverTest {

    private final Executor directExecutor = Runnable::run;

    @Test
    void shouldThrowWhenAllCollectionsFail() {
        CollectionParallelRetriever retriever = new CollectionParallelRetriever(
                failingRetrieverService(),
                directExecutor
        );

        assertThatThrownBy(() -> retriever.executeParallelRetrieval("query", List.of("a", "b"), 3))
                .isInstanceOf(RemoteException.class)
                .hasMessageContaining("全部目标检索失败");
    }

    @Test
    void shouldReturnSuccessfulChunksWhenOnlySomeCollectionsFail() {
        CollectionParallelRetriever retriever = new CollectionParallelRetriever(
                new RetrieverService() {
                    @Override
                    public List<RetrievedChunk> retrieve(RetrieveRequest retrieveParam) {
                        if ("bad".equals(retrieveParam.getCollectionName())) {
                            throw new IllegalStateException("embedding unavailable");
                        }
                        return List.of(RetrievedChunk.builder().id("chunk-1").text("ok").build());
                    }

                    @Override
                    public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam) {
                        throw new UnsupportedOperationException();
                    }
                },
                directExecutor
        );

        List<RetrievedChunk> chunks = retriever.executeParallelRetrieval("query", List.of("ok", "bad"), 3);

        assertThat(chunks).extracting(RetrievedChunk::getId).containsExactly("chunk-1");
    }

    private RetrieverService failingRetrieverService() {
        return new RetrieverService() {
            @Override
            public List<RetrievedChunk> retrieve(RetrieveRequest retrieveParam) {
                throw new IllegalStateException("embedding unavailable");
            }

            @Override
            public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
