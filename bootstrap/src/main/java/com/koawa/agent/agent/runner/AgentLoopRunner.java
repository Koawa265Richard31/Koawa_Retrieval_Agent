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
import com.koawa.agent.agent.planner.AgentPlanner;

import java.util.Objects;

public class AgentLoopRunner {

    private final AgentPlanner planner;
    private final AgentActionExecutor executor;

    public AgentLoopRunner(AgentPlanner planner,
                           AgentActionExecutor executor) {
        this.planner = Objects.requireNonNull(
                planner,
                "planner cannot be null"
        );
        this.executor = Objects.requireNonNull(
                executor,
                "executor cannot be null"
        );
    }

    public AgentState run(AgentState state) {
        Objects.requireNonNull(state);

        if (state.getCurrentStep() < 0) {
            throw new IllegalArgumentException(
                    "currentStep cannot be negative: " + state.getCurrentStep()
            );
        }

        if (state.getMaxSteps() <= 0) {
            throw new IllegalArgumentException(
                    "maxSteps must be positive: " + state.getMaxSteps()
            );
        }

        try {
            while (state.getCurrentStep() < state.getMaxSteps()) {
                int stepIndex = state.getCurrentStep();

                AgentAction action = Objects.requireNonNull(
                        planner.plan(state),
                        "planner returned null action"
                );

                AgentObservation observation = Objects.requireNonNull(
                        executor.execute(action, state),
                        "executor returned null observation"
                );

                AgentStep step = AgentStep.builder()
                        .action(action)
                        .observation(observation)
                        .stepIndex(stepIndex)
                        .build();

                state.getSteps().add(step);
                state.setCurrentStep(stepIndex + 1);

                if (action.getType().isTerminal()) {
                    finishTerminalAction(state, action, observation);
                    return state;
                }
            }

            state.setStopReason(AgentStopReason.MAX_STEPS);
            return state;
        } catch (RuntimeException exception) {
            state.setStopReason(AgentStopReason.ERROR);
            state.setErrorMessage(exception.getMessage());
            return state;
        }
    }

    private void finishTerminalAction(
            AgentState state,
            AgentAction action,
            AgentObservation observation
    ) {
        switch (action.getType()) {
            case FINAL_ANSWER -> {
                state.setStopReason(AgentStopReason.FINAL_ANSWER);
                state.setFinalAnswer(observation.getContent());
            }
            case ASK_CLARIFICATION -> {
                state.setStopReason(AgentStopReason.ASK_CLARIFICATION);
                state.setFinalAnswer(observation.getContent());
            }
            default -> throw new IllegalStateException(
                    "Unsupported terminal action: " + action.getType()
            );
        }
    }
}
