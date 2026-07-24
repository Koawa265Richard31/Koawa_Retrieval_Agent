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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedRetrievalTaskPlannerTests {

    private final RuleBasedRetrievalTaskPlanner planner = new RuleBasedRetrievalTaskPlanner();

    @Test
    void sufficientEvidenceProducesNoFollowUp() {
        RetrievalPlan result = planner.followUpPlan(
                plan(),
                new EvidenceEvaluation(true, List.of(), List.of(), 1D, "enough"),
                RetrievalBudget.defaults());

        assertTrue(result.tasks().isEmpty());
    }

    @Test
    void gapsBecomeBoundedFollowUpQueriesWithOriginalRouting() {
        EvidenceEvaluation evaluation = new EvidenceEvaluation(
                false,
                List.of(),
                List.of(new RetrievalGap("task-1", Set.of("approval"), "P6 approval process")),
                0.4D,
                "missing");

        RetrievalPlan result = planner.followUpPlan(plan(), evaluation, RetrievalBudget.defaults());

        assertEquals(1, result.tasks().size());
        RetrievalTask task = result.tasks().get(0);
        assertEquals("task-1", task.taskId());
        assertEquals("P6 approval process", task.question());
        assertEquals(List.of("kb-1"), task.knowledgeBaseIds());
        assertEquals(Set.of("approval"), task.requiredFacts());
    }

    private RetrievalPlan plan() {
        return new RetrievalPlan(
                List.of(new RetrievalTask(
                        "task-1", "P6 requirements", List.of("kb-1"),
                        Set.of("skills", "approval"), false)),
                "initial");
    }
}
