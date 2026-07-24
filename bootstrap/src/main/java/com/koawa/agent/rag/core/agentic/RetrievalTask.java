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

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record RetrievalTask(
        String taskId,
        String question,
        List<String> knowledgeBaseIds,
        Set<String> requiredFacts,
        boolean dependsOnPreviousEvidence) {

    public RetrievalTask {
        if (taskId == null || taskId.isBlank() || question == null || question.isBlank()) {
            throw new IllegalArgumentException("taskId and question are required");
        }
        knowledgeBaseIds = List.copyOf(Objects.requireNonNullElse(knowledgeBaseIds, List.of()));
        requiredFacts = Set.copyOf(Objects.requireNonNullElse(requiredFacts, Set.of()));
    }
}
