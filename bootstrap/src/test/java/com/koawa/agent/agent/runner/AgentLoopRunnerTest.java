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

package com.koawa.agent.agent.runner;

import com.koawa.agent.agent.domain.*;
import com.koawa.agent.agent.event.AgentEventSink;
import com.koawa.agent.agent.executor.AgentActionExecutor;
import com.koawa.agent.agent.executor.RoutingAgentActionExecutor;
import com.koawa.agent.agent.executor.handler.AskClarificationActionHandler;
import com.koawa.agent.agent.executor.handler.FinalAnswerActionHandler;
import com.koawa.agent.agent.planner.AgentPlanner;
import com.koawa.agent.framework.convention.ChatRequest;
import com.koawa.agent.infra.chat.LLMService;
import com.koawa.agent.agent.planner.ScriptedAgentPlanner;
import com.koawa.agent.rag.core.prompt.PromptTemplateLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentLoopRunnerTest {
    private AgentAction action(AgentActionType type) {
        return AgentAction.builder()
                .type(type)
                .thought("test")
                .build();
    }

    private AgentObservation observation(
            AgentActionType type,
            String content
    ) {
        return AgentObservation.builder()
                .actionType(type)
                .success(true)
                .content(content)
                .build();
    }

    private AgentState state(int maxSteps) {
        return AgentState.builder()
                .currentStep(0)
                .maxSteps(maxSteps)
                .build();
    }

    @Test
    void shouldRunUntilFinalAnswer() {
        AgentAction retrieve = action(AgentActionType.RETRIEVE_KB);
        AgentAction finish = action(AgentActionType.FINAL_ANSWER);

        ScriptedAgentPlanner planner = new ScriptedAgentPlanner(List.of(retrieve, finish));
        AgentActionExecutor executor = (action, state) ->
                observation(
                        action.getType(),
                        action.getType() == AgentActionType.FINAL_ANSWER
                                ? "最终回答"
                                : "检索结果"
                );

        AgentState result = new AgentLoopRunner(
                planner,
                executor,
                AgentEventSink.NOOP
        ).run(state(5));

        assertEquals(2, result.getCurrentStep());
        assertEquals(2, result.getSteps().size());
        assertEquals(AgentStopReason.FINAL_ANSWER, result.getStopReason());
        assertEquals("最终回答", result.getFinalAnswer());
    }

    @Test
    void shouldStopAtMaximumSteps() {
        List<AgentEvent> events = new ArrayList<>();

        ScriptedAgentPlanner planner = new ScriptedAgentPlanner(
                List.of(
                        action(AgentActionType.RETRIEVE_KB),
                        action(AgentActionType.CALL_MCP_TOOL)
                )
        );

        AgentActionExecutor executor = (action, state) ->
                observation(action.getType(), "检索结果");

        AgentState result = new AgentLoopRunner(
                planner,
                executor,
                events::add
        ).run(state(2));

        AgentEvent completed = events.get(events.size() - 1);
        assertEquals(2, result.getCurrentStep());
        assertEquals(2, result.getSteps().size());
        assertEquals(AgentStopReason.MAX_STEPS, result.getStopReason());
        assertNull(result.getFinalAnswer());
        assertAll(
                () -> assertEquals(
                        AgentEventType.TURN_COMPLETED,
                        completed.type()
                ),
                () -> assertEquals(
                        AgentStopReason.MAX_STEPS,
                        completed.stopReason()
                ),
                () -> assertNull(completed.content())
        );
    }

    @Test
    void shouldStoreRuntimeErrorInState() {
        List<AgentEvent> events = new ArrayList<>();
        ScriptedAgentPlanner planner = new ScriptedAgentPlanner(List.of(
                action(AgentActionType.RETRIEVE_KB)
        ));

        AgentActionExecutor executor = (action, state) -> {
            throw new IllegalStateException("executor failed");
        };

        AgentState result = new AgentLoopRunner(
                planner,
                executor,
                events::add
        ).run(state(3));

        AgentEvent failed = events.get(events.size() - 1);
        assertEquals(0, result.getCurrentStep());
        assertTrue(result.getSteps().isEmpty());
        assertEquals(AgentStopReason.ERROR, result.getStopReason());
        assertEquals("executor failed", result.getErrorMessage());
        assertAll(
                () -> assertEquals(AgentEventType.TURN_FAILED, failed.type()),
                () -> assertEquals(Boolean.FALSE, failed.success()),
                () -> assertEquals(
                        AgentStopReason.ERROR,
                        failed.stopReason()
                ),
                () -> assertEquals("executor failed", failed.errorMessage())
        );
    }

    @Test
    void shouldPublishStepEventsInOrder() {
        List<AgentEvent> events = new ArrayList<>();
        AgentEventSink eventSink = events::add;

        AgentAction finalAnswer = action(AgentActionType.FINAL_ANSWER);
        AgentPlanner planner = state -> {
            assertEquals(
                    List.of(
                            AgentEventType.TURN_STARTED,
                            AgentEventType.STEP_STARTED
                    ),
                    events.stream().map(AgentEvent::type).toList()
            );
            return finalAnswer;
        };
        AgentActionExecutor executor = (action, state) -> {
            assertEquals(
                    List.of(
                            AgentEventType.TURN_STARTED,
                            AgentEventType.STEP_STARTED,
                            AgentEventType.ACTION_PLANNED
                    ),
                    events.stream().map(AgentEvent::type).toList()
            );
            return observation(action.getType(), "最终回答");
        };

        AgentState state = AgentState.builder()
                .conversationId("conversation-1")
                .currentStep(0)
                .maxSteps(2)
                .build();

        new AgentLoopRunner(planner, executor, eventSink).run(state);

        AgentEvent turnStarted = events.get(0);
        AgentEvent stepStarted = events.get(1);
        AgentEvent actionPlanned = events.get(2);
        AgentEvent observationReceived = events.get(3);
        AgentEvent turnCompleted = events.get(4);
        assertAll(
                () -> assertEquals(
                        List.of(
                                AgentEventType.TURN_STARTED,
                                AgentEventType.STEP_STARTED,
                                AgentEventType.ACTION_PLANNED,
                                AgentEventType.OBSERVATION_RECEIVED,
                                AgentEventType.TURN_COMPLETED
                        ),
                        events.stream().map(AgentEvent::type).toList()
                ),
                () -> assertEquals(
                        "conversation-1",
                        turnStarted.conversationId()
                ),
                () -> assertEquals(0, turnStarted.stepIndex()),
                () -> assertEquals(
                        "conversation-1",
                        stepStarted.conversationId()
                ),
                () -> assertEquals(0, stepStarted.stepIndex()),
                () -> assertNull(stepStarted.actionType()),
                () -> assertNull(stepStarted.success()),
                () -> assertNull(stepStarted.stopReason()),
                () -> assertEquals(
                        AgentActionType.FINAL_ANSWER,
                        actionPlanned.actionType()
                ),
                () -> assertEquals(
                        AgentActionType.FINAL_ANSWER,
                        observationReceived.actionType()
                ),
                () -> assertEquals(Boolean.TRUE, observationReceived.success()),
                () -> assertEquals("最终回答", observationReceived.content()),
                () -> assertNull(observationReceived.errorMessage()),
                () -> assertEquals(
                        AgentStopReason.FINAL_ANSWER,
                        turnCompleted.stopReason()
                ),
                () -> assertEquals("最终回答", turnCompleted.content())
        );
    }

    @Test
    void shouldContinueWhenEventSinkFails() {
        AgentAction finalAnswer = action(AgentActionType.FINAL_ANSWER);
        AgentPlanner planner = state -> finalAnswer;
        AgentActionExecutor executor = (action, state) ->
                observation(action.getType(), "最终回答");
        AgentEventSink failingSink = event -> {
            throw new IllegalStateException("event sink failed");
        };

        AgentState result = new AgentLoopRunner(
                planner,
                executor,
                failingSink
        ).run(state(2));

        assertAll(
                () -> assertEquals(1, result.getCurrentStep()),
                () -> assertEquals(1, result.getSteps().size()),
                () -> assertEquals(
                        AgentStopReason.FINAL_ANSWER,
                        result.getStopReason()
                ),
                () -> assertEquals("最终回答", result.getFinalAnswer()),
                () -> assertNull(result.getErrorMessage())
        );
    }

    @Test
    void shouldStopForClarificationThroughRoutingExecutor() {

        List<AgentAction> actions = List.of(
                AgentAction.builder()
                        .type(AgentActionType.ASK_CLARIFICATION)
                        .arguments(Map.of(
                                "question", "澄清问题"
                        ))
                        .build()
        );

        ScriptedAgentPlanner planner = new ScriptedAgentPlanner(actions);

        AskClarificationActionHandler handler = new AskClarificationActionHandler();

        RoutingAgentActionExecutor executor = new RoutingAgentActionExecutor(
                List.of(handler)
        );

        AgentLoopRunner runner = new AgentLoopRunner(
                planner,
                executor,
                AgentEventSink.NOOP
        );

        AgentState state = AgentState.builder()
                .currentStep(0)
                .maxSteps(2)
                .build();

        AgentState resultState = runner.run(state);

        assertEquals(1, resultState.getCurrentStep());
        assertEquals(1, resultState.getSteps().size());
        assertEquals(AgentStopReason.ASK_CLARIFICATION, resultState.getStopReason());
        assertEquals("澄清问题", resultState.getFinalAnswer());

    }

    @Test
    void shouldStopForFinalAnswerThroughRoutingExecutor() {
        LLMService llmService = mock(LLMService.class);
        PromptTemplateLoader promptTemplateLoader =
                mock(PromptTemplateLoader.class);

        Map<String, String> expectedSlots = Map.of(
                "original_question", "用户问题",
                "observations", "无历史 Observation"
        );

        when(promptTemplateLoader.render(
                "prompt/agent-final-answer.st",
                expectedSlots
        )).thenReturn("渲染后的 Prompt");

        when(llmService.chat(any(ChatRequest.class)))
                .thenReturn("最终回答");

        AgentAction finalAnswer = AgentAction.builder()
                .type(AgentActionType.FINAL_ANSWER)
                .build();

        ScriptedAgentPlanner planner =
                new ScriptedAgentPlanner(List.of(finalAnswer));

        RoutingAgentActionExecutor executor =
                new RoutingAgentActionExecutor(List.of(
                        new FinalAnswerActionHandler(
                                llmService,
                                promptTemplateLoader
                        )
                ));

        AgentState state = AgentState.builder()
                .originalQuestion("用户问题")
                .currentStep(0)
                .maxSteps(2)
                .build();

        AgentState result =
                new AgentLoopRunner(
                        planner,
                        executor,
                        AgentEventSink.NOOP
                ).run(state);

        assertEquals(1, result.getCurrentStep());
        assertEquals(1, result.getSteps().size());
        assertEquals(AgentStopReason.FINAL_ANSWER, result.getStopReason());
        assertEquals("最终回答", result.getFinalAnswer());
        assertNull(result.getErrorMessage());

        verify(promptTemplateLoader).render(
                "prompt/agent-final-answer.st",
                expectedSlots
        );
        verify(llmService).chat(any(ChatRequest.class));
    }
}
