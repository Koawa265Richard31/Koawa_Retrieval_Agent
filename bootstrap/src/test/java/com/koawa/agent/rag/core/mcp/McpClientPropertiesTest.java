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

package com.koawa.agent.rag.core.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class McpClientPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void shouldUseSafeDefaultRequestTimeout() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(McpClientProperties.class)
                    .getRequestTimeout())
                    .isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void shouldBindRequestTimeout() {
        contextRunner
                .withPropertyValues("rag.mcp.request-timeout=5s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(McpClientProperties.class)
                            .getRequestTimeout())
                            .isEqualTo(Duration.ofSeconds(5));
                });
    }

    @Test
    void shouldRejectZeroRequestTimeout() {
        contextRunner
                .withPropertyValues("rag.mcp.request-timeout=0ms")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(McpClientProperties.class)
    static class PropertiesConfiguration {
    }
}
