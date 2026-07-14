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
import com.koawa.agent.agent.executor.AgentActionExecutor;
import com.koawa.agent.agent.planner.ScriptedAgentPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

        AgentState result = new AgentLoopRunner(planner, executor).run(state(5));

        assertEquals(2, result.getCurrentStep());
        assertEquals(2, result.getSteps().size());
        assertEquals(AgentStopReason.FINAL_ANSWER, result.getStopReason());
        assertEquals("最终回答", result.getFinalAnswer());
    }

    @Test
    void shouldStopAtMaximumSteps() {

        ScriptedAgentPlanner planner = new ScriptedAgentPlanner(
                List.of(
                        action(AgentActionType.RETRIEVE_KB),
                        action(AgentActionType.CALL_MCP_TOOL)
                )
        );

        AgentActionExecutor executor = (action, state) ->
                observation(action.getType(), "检索结果");

        AgentState result = new AgentLoopRunner(planner, executor).run(state(2));

        assertEquals(2, result.getCurrentStep());
        assertEquals(2, result.getSteps().size());
        assertEquals(AgentStopReason.MAX_STEPS, result.getStopReason());
        assertNull(result.getFinalAnswer());
    }

    @Test
    void shouldStoreRuntimeErrorInState() {
        ScriptedAgentPlanner planner = new ScriptedAgentPlanner(List.of(
                action(AgentActionType.RETRIEVE_KB)
        ));

        AgentActionExecutor executor = (action, state) -> {
            throw new IllegalStateException("executor failed");
        };

        AgentState result = new AgentLoopRunner(planner, executor).run(state(3));

        assertEquals(0, result.getCurrentStep());
        assertTrue(result.getSteps().isEmpty());
        assertEquals(AgentStopReason.ERROR, result.getStopReason());
        assertEquals("executor failed", result.getErrorMessage());
    }
}
