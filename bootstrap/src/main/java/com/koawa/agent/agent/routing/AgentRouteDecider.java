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

import java.util.Objects;

public final class AgentRouteDecider {

    private final AgentRuntimeProperties properties;

    public AgentRouteDecider(AgentRuntimeProperties properties) {
        this.properties = Objects.requireNonNull(
                properties,
                "properties cannot be null"
        );
    }

    public boolean shouldUseAgent(
            String conversationId,
            String userId
    ) {
        if (!properties.isEnabled()) {
            return false;
        }

        int rolloutPercentage = properties.getRolloutPercentage();
        if (rolloutPercentage <= 0) {
            return false;
        }

        String routingKey = resolveRoutingKey(conversationId, userId);
        if (routingKey == null) {
            return false;
        }

        int bucket = Math.floorMod(routingKey.hashCode(), 100);
        return bucket < rolloutPercentage;
    }

    private String resolveRoutingKey(
            String conversationId,
            String userId
    ) {
        if (userId != null && !userId.isBlank()) {
            return userId.trim();
        }

        if (conversationId != null && !conversationId.isBlank()) {
            return conversationId.trim();
        }

        return null;
    }
}
