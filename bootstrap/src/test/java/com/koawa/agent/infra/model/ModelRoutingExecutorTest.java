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

package com.koawa.agent.infra.model;

import com.koawa.agent.infra.config.AIModelProperties;
import com.koawa.agent.infra.enums.ModelCapability;
import com.koawa.agent.infra.http.ModelClientErrorType;
import com.koawa.agent.infra.http.ModelClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelRoutingExecutorTest {

    private ModelHealthStore healthStore;
    private ModelRoutingExecutor executor;

    @BeforeEach
    void setUp() {
        healthStore = mock(ModelHealthStore.class);
        when(healthStore.allowCall(anyString())).thenReturn(true);
        executor = new ModelRoutingExecutor(healthStore);
    }

    @Test
    void shouldStopFallbackWithoutMarkingFailureWhenDeadlineExpires() {
        AtomicInteger calls = new AtomicInteger();

        ModelClientException result = assertThrows(
                ModelClientException.class,
                () -> executor.executeWithFallback(
                        ModelCapability.CHAT,
                        List.of(target("model-1"), target("model-2")),
                        ModelTarget::id,
                        (client, target) -> {
                            calls.incrementAndGet();
                            throw new ModelClientException(
                                    "deadline exceeded",
                                    ModelClientErrorType.DEADLINE_EXCEEDED,
                                    null
                            );
                        }
                )
        );

        assertEquals(
                ModelClientErrorType.DEADLINE_EXCEEDED,
                result.getErrorType()
        );
        assertEquals(1, calls.get());
        verify(healthStore).allowCall("model-1");
        verify(healthStore, never()).allowCall("model-2");
        verify(healthStore, never()).markFailure(anyString());
        verify(healthStore, never()).markSuccess(anyString());
    }

    @Test
    void shouldContinueFallbackForOrdinaryNetworkFailure() {
        AtomicInteger calls = new AtomicInteger();

        String result = executor.executeWithFallback(
                ModelCapability.CHAT,
                List.of(target("model-1"), target("model-2")),
                ModelTarget::id,
                (client, target) -> {
                    calls.incrementAndGet();
                    if (target.id().equals("model-1")) {
                        throw new ModelClientException(
                                "network failed",
                                ModelClientErrorType.NETWORK_ERROR,
                                null
                        );
                    }
                    return "answer";
                }
        );

        assertEquals("answer", result);
        assertEquals(2, calls.get());
        verify(healthStore).markFailure("model-1");
        verify(healthStore).markSuccess("model-2");
    }

    private ModelTarget target(String id) {
        AIModelProperties.ModelCandidate candidate =
                new AIModelProperties.ModelCandidate();
        candidate.setId(id);
        candidate.setProvider("test-provider");

        return new ModelTarget(
                id,
                candidate,
                new AIModelProperties.ProviderConfig()
        );
    }
}
