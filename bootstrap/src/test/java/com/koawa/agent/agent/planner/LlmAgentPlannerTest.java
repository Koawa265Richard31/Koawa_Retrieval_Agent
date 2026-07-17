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

package com.koawa.agent.agent.planner;

import com.koawa.agent.agent.domain.AgentAction;
import com.koawa.agent.agent.domain.AgentActionType;
import com.koawa.agent.agent.domain.AgentObservation;
import com.koawa.agent.agent.domain.AgentState;
import com.koawa.agent.agent.domain.AgentStep;
import com.koawa.agent.agent.parser.AgentActionParser;
import com.koawa.agent.framework.convention.ChatRequest;
import com.koawa.agent.infra.chat.LLMService;
import com.koawa.agent.rag.core.mcp.McpToolRegistry;
import com.koawa.agent.rag.core.prompt.PromptTemplateLoader;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LlmAgentPlannerTest {

    private static final String PROMPT_PATH = "prompt/agent-planner.st";
    private static final String RENDERED_PROMPT = "rendered planner prompt";

    private LLMService llmService;
    private PromptTemplateLoader promptTemplateLoader;
    private AgentActionParser actionParser;
    private McpToolRegistry toolRegistry;
    private LlmAgentPlanner planner;
    private Map<String, String> renderedSlots;

    @BeforeEach
    void setUp() {
        llmService = mock(LLMService.class);
        promptTemplateLoader = mock(PromptTemplateLoader.class);
        actionParser = mock(AgentActionParser.class);
        toolRegistry = mock(McpToolRegistry.class);

        when(toolRegistry.listAllTools()).thenReturn(List.of());
        when(promptTemplateLoader.render(eq(PROMPT_PATH), anyMap()))
                .thenAnswer(invocation -> {
                    renderedSlots = invocation.getArgument(1);
                    return RENDERED_PROMPT;
                });

        planner = new LlmAgentPlanner(
                llmService,
                promptTemplateLoader,
                actionParser,
                toolRegistry
        );
    }

    @Test
    void shouldParseLlmResponseIntoAction() {
        String rawAction = """
                {
                  "type": "RETRIEVE_KB",
                  "thought": "先检索知识库",
                  "arguments": {
                    "query": "退款规则",
                    "topK": 5
                  }
                }
                """;

        AgentAction expectedAction = AgentAction.builder()
                .type(AgentActionType.RETRIEVE_KB)
                .thought("先检索知识库")
                .arguments(Map.of(
                        "query", "退款规则",
                        "topK", 5
                ))
                .build();

        stubPlannerResponse(rawAction, expectedAction);

        AgentAction result = planner.plan(state(" 如何申请退款？ "));

        assertSame(expectedAction, result);
        assertNotNull(renderedSlots);
        assertAll(
                () -> assertEquals(
                        "如何申请退款？",
                        renderedSlots.get("original_question")
                ),
                () -> assertEquals("0", renderedSlots.get("current_step")),
                () -> assertEquals("3", renderedSlots.get("max_steps")),
                () -> assertEquals("无历史步骤", renderedSlots.get("steps")),
                () -> assertEquals("无可用 MCP 工具", renderedSlots.get("tools"))
        );

        ArgumentCaptor<ChatRequest> requestCaptor =
                ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).chat(requestCaptor.capture());

        ChatRequest request = requestCaptor.getValue();
        assertAll(
                () -> assertEquals(1, request.getMessages().size()),
                () -> assertEquals(
                        RENDERED_PROMPT,
                        request.getMessages().get(0).getContent()
                ),
                () -> assertEquals(0.1D, request.getTemperature()),
                () -> assertEquals(Boolean.FALSE, request.getThinking())
        );
        verify(actionParser).parse(rawAction);
    }

    @Test
    void shouldIncludeFailedObservationInPrompt() {
        AgentAction expectedAction = stubFinalAnswerResponse();

        AgentAction failedAction = AgentAction.builder()
                .type(AgentActionType.CALL_MCP_TOOL)
                .arguments(Map.of(
                        "toolId", "weather",
                        "params", Map.of("city", "上海")
                ))
                .build();

        AgentObservation failedObservation = AgentObservation.builder()
                .actionType(AgentActionType.CALL_MCP_TOOL)
                .success(false)
                .content(" ")
                .errorMessage("MCP tool execution failed: timeout")
                .build();

        AgentStep failedStep = AgentStep.builder()
                .stepIndex(0)
                .action(failedAction)
                .observation(failedObservation)
                .build();

        AgentState state = AgentState.builder()
                .originalQuestion("上海天气怎么样？")
                .currentStep(1)
                .maxSteps(3)
                .steps(List.of(failedStep))
                .build();

        AgentAction result = planner.plan(state);

        assertSame(expectedAction, result);
        String steps = renderedSlots.get("steps");
        assertAll(
                () -> assertTrue(steps.contains("Step 0")),
                () -> assertTrue(steps.contains("actionType: CALL_MCP_TOOL")),
                () -> assertTrue(steps.contains("\"toolId\":\"weather\"")),
                () -> assertTrue(steps.contains("observationSuccess: false")),
                () -> assertTrue(steps.contains("observationContent: 无")),
                () -> assertTrue(steps.contains(
                        "observationError: MCP tool execution failed: timeout"
                ))
        );
    }

    @Test
    void shouldIncludeMcpToolDefinitionInPrompt() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("city", Map.of(
                "type", "string",
                "description", "城市名称"
        ));

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                properties,
                List.of("city"),
                null,
                null,
                null
        );

        McpSchema.Tool weatherTool = McpSchema.Tool.builder()
                .name("weather")
                .description("查询城市天气")
                .inputSchema(inputSchema)
                .build();

        when(toolRegistry.listAllTools()).thenReturn(List.of(weatherTool));
        stubFinalAnswerResponse();

        planner.plan(state("上海天气怎么样？"));

        String tools = renderedSlots.get("tools");
        assertAll(
                () -> assertTrue(tools.contains("Tool 1")),
                () -> assertTrue(tools.contains("toolId: weather")),
                () -> assertTrue(tools.contains("description: 查询城市天气")),
                () -> assertTrue(tools.contains("inputSchema:")),
                () -> assertTrue(tools.contains("\"city\"")),
                () -> assertTrue(tools.contains("城市名称"))
        );
    }

    @Test
    void shouldRejectBlankOriginalQuestion() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> planner.plan(state(" "))
        );

        assertEquals(
                "Original question must be a non-blank string",
                exception.getMessage()
        );
        verifyNoInteractions(
                promptTemplateLoader,
                llmService,
                actionParser,
                toolRegistry
        );
    }

    @Test
    void shouldRejectBlankLlmResponse() {
        String rawAction = " ";
        when(llmService.chat(any(ChatRequest.class))).thenReturn(rawAction);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> planner.plan(state("测试问题"))
        );

        assertEquals(
                "Agent planner LLM returned a blank action",
                exception.getMessage()
        );
        verifyNoInteractions(actionParser);
    }

    @Test
    void shouldPropagateParserFailure() {
        String rawAction = "not-json";
        IllegalArgumentException parserFailure =
                new IllegalArgumentException("Agent action is invalid JSON");

        when(llmService.chat(any(ChatRequest.class))).thenReturn(rawAction);
        when(actionParser.parse(rawAction)).thenThrow(parserFailure);

        IllegalArgumentException result = assertThrows(
                IllegalArgumentException.class,
                () -> planner.plan(state("测试问题"))
        );

        assertSame(parserFailure, result);
    }

    private AgentState state(String question) {
        return AgentState.builder()
                .originalQuestion(question)
                .currentStep(0)
                .maxSteps(3)
                .build();
    }

    private AgentAction stubFinalAnswerResponse() {
        String rawAction = """
                {
                  "type": "FINAL_ANSWER",
                  "thought": "已有信息足够",
                  "arguments": {}
                }
                """;

        AgentAction action = AgentAction.builder()
                .type(AgentActionType.FINAL_ANSWER)
                .thought("已有信息足够")
                .arguments(Map.of())
                .build();

        stubPlannerResponse(rawAction, action);
        return action;
    }

    private void stubPlannerResponse(
            String rawAction,
            AgentAction parsedAction
    ) {
        when(llmService.chat(any(ChatRequest.class))).thenReturn(rawAction);
        when(actionParser.parse(rawAction)).thenReturn(parsedAction);
    }
}
