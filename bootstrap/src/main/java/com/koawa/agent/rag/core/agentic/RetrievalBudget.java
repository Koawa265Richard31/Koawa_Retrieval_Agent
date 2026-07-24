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

package com.koawa.agent.rag.core.agentic;

import java.time.Duration;

public record RetrievalBudget(
        int maxIterations,
        int maxSubQueries,
        int maxRetrievedChunks,
        Duration timeout) {

    public static RetrievalBudget defaults() {
        return new RetrievalBudget(2, 6, 40, Duration.ofSeconds(8));
    }

    public RetrievalBudget {
        if (maxIterations < 1 || maxSubQueries < 1 || maxRetrievedChunks < 1) {
            throw new IllegalArgumentException("retrieval budget limits must be positive");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("retrieval timeout must be positive");
        }
    }
}
