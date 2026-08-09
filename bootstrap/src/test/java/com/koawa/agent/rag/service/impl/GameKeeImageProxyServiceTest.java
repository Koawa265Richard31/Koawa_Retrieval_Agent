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

package com.koawa.agent.rag.service.impl;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class GameKeeImageProxyServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final GameKeeImageProxyService service = new GameKeeImageProxyService(s3Client);

    @Test
    void rejectsNonHttpsUrl() {
        assertThatThrownBy(() -> service.resolve("http://cdnimg-v2.gamekee.com/a.png"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownHost() {
        assertThatThrownBy(() -> service.resolve("https://evil.example.com/a.png"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedUrl() {
        assertThatThrownBy(() -> service.resolve("not-a-url"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
