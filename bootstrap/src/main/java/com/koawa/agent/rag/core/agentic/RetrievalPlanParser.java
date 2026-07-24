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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RetrievalPlanParser {

    private final Gson gson = new Gson();

    public RetrievalPlan parse(String raw, RetrievalPlan currentPlan, RetrievalBudget budget) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("retrieval plan response is empty");
        }
        Payload payload;
        try {
            payload = gson.fromJson(unwrapCodeFence(raw.trim()), Payload.class);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("retrieval plan is not valid JSON", exception);
        }
        if (payload == null || payload.queries == null
                || payload.queries.size() > budget.maxSubQueries()) {
            throw new IllegalArgumentException("retrieval plan fields are invalid");
        }
        Map<String, RetrievalTask> originals = new LinkedHashMap<>();
        currentPlan.tasks().forEach(task -> originals.put(task.taskId(), task));
        Set<String> seen = new HashSet<>();
        List<RetrievalTask> tasks = payload.queries.stream()
                .map(query -> toTask(query, originals, seen))
                .toList();
        return new RetrievalPlan(tasks, payload.rationale);
    }

    private RetrievalTask toTask(
            QueryPayload query,
            Map<String, RetrievalTask> originals,
            Set<String> seen) {
        if (query == null || query.taskId == null || query.query == null
                || query.query.isBlank() || !seen.add(query.taskId)) {
            throw new IllegalArgumentException("retrieval query is invalid or duplicated");
        }
        RetrievalTask original = originals.get(query.taskId);
        if (original == null) {
            throw new IllegalArgumentException("retrieval plan references unknown task: " + query.taskId);
        }
        return new RetrievalTask(
                original.taskId(),
                query.query,
                original.knowledgeBaseIds(),
                original.requiredFacts(),
                original.dependsOnPreviousEvidence());
    }

    private String unwrapCodeFence(String value) {
        if (!value.startsWith("```")) {
            return value;
        }
        int firstLineEnd = value.indexOf('\n');
        int closingFence = value.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
            throw new IllegalArgumentException("incomplete JSON code fence");
        }
        return value.substring(firstLineEnd + 1, closingFence).trim();
    }

    private static final class Payload {
        private List<QueryPayload> queries;
        private String rationale;
    }

    private static final class QueryPayload {
        private String taskId;
        private String query;
    }
}
