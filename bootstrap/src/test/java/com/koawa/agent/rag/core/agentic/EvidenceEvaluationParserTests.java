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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceEvaluationParserTests {

    private final EvidenceEvaluationParser parser = new EvidenceEvaluationParser();
    private final RetrievalPlan plan = new RetrievalPlan(
            List.of(new RetrievalTask(
                    "task-1", "question", List.of(), Set.of("fact"), false)),
            "test");

    @Test
    void shouldParseFencedStrictJson() {
        String json = """
                ```json
                {
                  "sufficient": false,
                  "assessments": [{
                    "taskId": "task-1",
                    "status": "PARTIALLY_SUPPORTED",
                    "coveredFacts": [],
                    "missingFacts": ["fact"],
                    "explanation": "missing"
                  }],
                  "gaps": [{
                    "taskId": "task-1",
                    "missingFacts": ["fact"],
                    "suggestedQuery": "query"
                  }],
                  "confidence": 0.8,
                  "explanation": "needs retrieval"
                }
                ```
                """;

        EvidenceEvaluation result = parser.parse(json, plan);

        assertEquals(0.8, result.confidence());
        assertEquals(TaskEvidenceStatus.PARTIALLY_SUPPORTED,
                result.assessments().get(0).status());
    }

    @Test
    void shouldRejectInvalidJsonAndUnknownTasks() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("{", plan));
        String unknown = """
                {"sufficient":true,"assessments":[
                  {"taskId":"invented","status":"SUPPORTED"}
                ],"gaps":[],"confidence":1,"explanation":"x"}
                """;
        assertThrows(IllegalArgumentException.class, () -> parser.parse(unknown, plan));
    }

    @Test
    void shouldRequireEveryTaskExactlyOnce() {
        String missing = """
                {"sufficient":true,"assessments":[],"gaps":[],
                 "confidence":1,"explanation":"x"}
                """;
        assertThrows(IllegalArgumentException.class, () -> parser.parse(missing, plan));
    }
}
