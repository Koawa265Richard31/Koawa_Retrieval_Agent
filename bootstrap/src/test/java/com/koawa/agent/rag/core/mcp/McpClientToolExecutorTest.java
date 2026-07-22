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

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class McpClientToolExecutorTest {

    private final McpSyncClient mcpClient = mock(McpSyncClient.class);
    private final Tool tool = Tool.builder()
            .name("weather")
            .description("Query weather")
            .inputSchema(new JsonSchema(
                    "object",
                    Map.of(),
                    List.of(),
                    null,
                    null,
                    null
            ))
            .build();
    private final McpClientToolExecutor executor =
            new McpClientToolExecutor(mcpClient, tool);

    @Test
    void shouldExecuteWithParametersWithoutLoggingValues(
            CapturedOutput output
    ) {
        Map<String, Object> parameters = Map.of(
                "city", "sensitive-city",
                "token", "sensitive-token"
        );
        CallToolResult expected = CallToolResult.builder()
                .content(List.of(new TextContent("sensitive-result")))
                .isError(false)
                .build();
        when(mcpClient.callTool(any(CallToolRequest.class)))
                .thenReturn(expected);

        CallToolResult actual = executor.execute(parameters);

        assertThat(actual).isSameAs(expected);
        verify(mcpClient).callTool(
                new CallToolRequest("weather", parameters)
        );
        assertThat(output)
                .contains("toolId=weather")
                .contains("parameterCount=2")
                .contains("contentSize=1")
                .doesNotContain(
                        "sensitive-city",
                        "sensitive-token",
                        "sensitive-result"
                );
    }

    @Test
    void shouldLogOnlyErrorTypeWhenRemoteCallFails(
            CapturedOutput output
    ) {
        Map<String, Object> parameters = Map.of(
                "token", "sensitive-token"
        );
        when(mcpClient.callTool(any(CallToolRequest.class)))
                .thenThrow(new IllegalStateException("sensitive-reason"));

        CallToolResult result = executor.execute(parameters);

        assertThat(result.isError()).isTrue();
        assertThat(output)
                .contains("toolId=weather")
                .contains("parameterCount=1")
                .contains("errorType=IllegalStateException")
                .doesNotContain(
                        "sensitive-token",
                        "sensitive-reason"
                );
    }
}
