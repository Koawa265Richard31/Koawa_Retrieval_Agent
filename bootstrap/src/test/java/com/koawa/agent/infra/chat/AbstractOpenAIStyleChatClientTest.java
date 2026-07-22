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

package com.koawa.agent.infra.chat;

import com.koawa.agent.framework.convention.ChatRequest;
import com.koawa.agent.infra.http.ModelClientErrorType;
import com.koawa.agent.infra.http.ModelClientException;
import com.koawa.agent.infra.model.ModelTarget;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractOpenAIStyleChatClientTest {

    private static final Instant NOW =
            Instant.parse("2026-07-20T12:00:00Z");

    private TestChatClient chatClient;
    private OkHttpClient syncHttpClient;

    @BeforeEach
    void setUp() {
        chatClient = new TestChatClient();
        syncHttpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(45))
                .build();
        setSyncHttpClient(syncHttpClient);
    }

    @Test
    void shouldReuseConfiguredClientWithoutDeadline() {
        OkHttpClient result = chatClient.resolveSyncHttpClient(
                ChatRequest.builder().build(),
                NOW
        );

        assertSame(syncHttpClient, result);
    }

    @Test
    void shouldReuseConfiguredClientWhenDeadlineIsMorePermissive() {
        OkHttpClient result = chatClient.resolveSyncHttpClient(
                requestWithDeadline(NOW.plusSeconds(80)),
                NOW
        );

        assertSame(syncHttpClient, result);
        assertEquals(45_000, result.callTimeoutMillis());
    }

    @Test
    void shouldUseRemainingDeadlineWhenItIsShorter() {
        OkHttpClient result = chatClient.resolveSyncHttpClient(
                requestWithDeadline(NOW.plusSeconds(5)),
                NOW
        );

        assertNotSame(syncHttpClient, result);
        assertEquals(5_000, result.callTimeoutMillis());
        assertEquals(45_000, syncHttpClient.callTimeoutMillis());
    }

    @Test
    void shouldRecalculateRemainingBudgetForFallbackAttempt() {
        ChatRequest request =
                requestWithDeadline(NOW.plusSeconds(12));

        OkHttpClient firstAttempt =
                chatClient.resolveSyncHttpClient(request, NOW);
        OkHttpClient fallbackAttempt =
                chatClient.resolveSyncHttpClient(
                        request,
                        NOW.plusSeconds(10)
                );

        assertEquals(12_000, firstAttempt.callTimeoutMillis());
        assertEquals(2_000, fallbackAttempt.callTimeoutMillis());
    }

    @Test
    void shouldUseRemainingDeadlineWhenConfiguredTimeoutIsUnlimited() {
        OkHttpClient unlimitedClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ZERO)
                .build();
        setSyncHttpClient(unlimitedClient);

        OkHttpClient result = chatClient.resolveSyncHttpClient(
                requestWithDeadline(NOW.plusSeconds(5)),
                NOW
        );

        assertEquals(5_000, result.callTimeoutMillis());
    }

    @Test
    void shouldClampPositiveSubMillisecondBudgetToOneMillisecond() {
        OkHttpClient result = chatClient.resolveSyncHttpClient(
                requestWithDeadline(NOW.plusNanos(500_000)),
                NOW
        );

        assertEquals(1, result.callTimeoutMillis());
    }

    @Test
    void shouldRejectExpiredDeadlineBeforeStartingCall() {
        ModelClientException exception = assertThrows(
                ModelClientException.class,
                () -> chatClient.resolveSyncHttpClient(
                        requestWithDeadline(NOW),
                        NOW
                )
        );

        assertEquals(
                ModelClientErrorType.DEADLINE_EXCEEDED,
                exception.getErrorType()
        );
        assertTrue(exception.getMessage().contains("调用期限已到"));
    }

    private ChatRequest requestWithDeadline(Instant deadlineAt) {
        return ChatRequest.builder()
                .deadlineAt(deadlineAt)
                .build();
    }

    private void setSyncHttpClient(OkHttpClient client) {
        ReflectionTestUtils.setField(
                chatClient,
                "syncHttpClient",
                client
        );
    }

    private static final class TestChatClient
            extends AbstractOpenAIStyleChatClient {

        @Override
        public String provider() {
            return "test-provider";
        }

        @Override
        public String chat(
                ChatRequest request,
                ModelTarget target
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamChat(
                ChatRequest request,
                StreamCallback callback,
                ModelTarget target
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
