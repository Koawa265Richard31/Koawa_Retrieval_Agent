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
import com.koawa.agent.agent.domain.AgentState;

import java.util.List;
import java.util.Objects;

public class ScriptedAgentPlanner implements AgentPlanner {

    private final List<AgentAction> actions;

    public ScriptedAgentPlanner(List<AgentAction> actions) {
        Objects.requireNonNull(actions, "actions cannot be null");
        this.actions = List.copyOf(actions);
    }


    @Override
    public AgentAction plan(AgentState state) {
        Objects.requireNonNull(state, "state cannot be null");

        int currentStep = state.getCurrentStep();

        if (currentStep < 0) {
            throw new IllegalArgumentException("currentStep cannot be negative: " + currentStep);
        }

        if (currentStep >= actions.size()) {
            throw new IllegalStateException("No scripted actions for step: " + currentStep);
        }

        return actions.get(currentStep);
    }
}
