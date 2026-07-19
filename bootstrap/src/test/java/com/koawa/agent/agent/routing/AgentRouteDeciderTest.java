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

package com.koawa.agent.agent.routing;

import com.koawa.agent.agent.config.AgentRuntimeProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRouteDeciderTest {

    @Test
    void shouldUseOldRagWhenAgentIsDisabled() {
        AgentRouteDecider decider = decider(false, 100);

        assertFalse(decider.shouldUseAgent("conversation-1", "user-1"));
    }

    @Test
    void shouldUseOldRagWhenRolloutPercentageIsZero() {
        AgentRouteDecider decider = decider(true, 0);

        assertFalse(decider.shouldUseAgent("conversation-1", "user-1"));
    }

    @Test
    void shouldUseAgentWhenRolloutPercentageIsOneHundred() {
        AgentRouteDecider decider = decider(true, 100);

        assertTrue(decider.shouldUseAgent("conversation-1", "user-1"));
    }

    @Test
    void shouldUseOldRagWhenRoutingKeyIsMissing() {
        AgentRouteDecider decider = decider(true, 100);

        assertFalse(decider.shouldUseAgent(" ", null));
    }

    private AgentRouteDecider decider(
            boolean enabled,
            int rolloutPercentage
    ) {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setEnabled(enabled);
        properties.setRolloutPercentage(rolloutPercentage);
        return new AgentRouteDecider(properties);
    }
}
