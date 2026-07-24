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

package com.koawa.agent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "rag.agentic-retrieval")
public class AgenticRetrievalProperties {

    private Mode mode = Mode.OFF;
    private int rolloutPercentage = 0;
    /**
     * Compatibility switch used by AR1/AR2 deployments. Prefer {@link #mode}.
     */
    private boolean shadowEnabled = false;
    private Duration evaluatorTimeout = Duration.ofSeconds(8);
    private Duration plannerTimeout = Duration.ofSeconds(8);
    private Duration timeout = Duration.ofSeconds(8);
    private int maxIterations = 2;
    private int maxSubQueries = 6;
    private int maxRetrievedChunks = 40;
    private int maxEvidenceItems = 20;
    private int maxEvidenceChars = 1200;

    public Mode effectiveMode() {
        return mode == Mode.OFF && shadowEnabled ? Mode.SHADOW : mode;
    }

    public enum Mode {
        OFF,
        SHADOW,
        ACTIVE
    }
}
