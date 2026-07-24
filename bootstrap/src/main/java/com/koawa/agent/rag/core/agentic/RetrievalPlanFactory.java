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

import com.koawa.agent.rag.core.intent.NodeScore;
import com.koawa.agent.rag.dto.SubQuestionIntent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.koawa.agent.rag.constant.RAGConstant.MULTI_CHANNEL_KEY;

@Component
public class RetrievalPlanFactory {

    public RetrievalPlanSeed initialPlan(List<SubQuestionIntent> subIntents) {
        List<RetrievalTask> tasks = new ArrayList<>();
        Map<String, String> taskIdByIntentId = new LinkedHashMap<>();
        Map<String, SubQuestionIntent> intentByTaskId = new LinkedHashMap<>();
        if (subIntents == null) {
            return new RetrievalPlanSeed(
                    new RetrievalPlan(List.of(), "no sub-questions"),
                    taskIdByIntentId,
                    intentByTaskId);
        }
        for (int index = 0; index < subIntents.size(); index++) {
            SubQuestionIntent intent = subIntents.get(index);
            if (intent == null || intent.subQuestion() == null
                    || intent.subQuestion().isBlank()) {
                continue;
            }
            String taskId = "retrieval-task-" + (index + 1);
            List<NodeScore> scores = intent.nodeScores() == null ? List.of() : intent.nodeScores();
            List<String> knowledgeBaseIds = scores.stream()
                    .map(NodeScore::getNode)
                    .filter(java.util.Objects::nonNull)
                    .map(node -> node.getKbId())
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .toList();
            tasks.add(new RetrievalTask(
                    taskId,
                    intent.subQuestion(),
                    knowledgeBaseIds,
                    Set.of(intent.subQuestion()),
                    false));
            intentByTaskId.put(taskId, intent);
            if (scores.isEmpty()) {
                taskIdByIntentId.putIfAbsent(MULTI_CHANNEL_KEY, taskId);
            } else {
                scores.stream()
                        .map(NodeScore::getNode)
                        .filter(java.util.Objects::nonNull)
                        .map(node -> node.getId())
                        .filter(value -> value != null && !value.isBlank())
                        .forEach(intentId -> taskIdByIntentId.putIfAbsent(intentId, taskId));
            }
        }
        return new RetrievalPlanSeed(
                new RetrievalPlan(tasks, "initial retrieval plan"),
                taskIdByIntentId,
                intentByTaskId);
    }

    public List<SubQuestionIntent> toSubIntents(
            RetrievalPlan followUpPlan,
            RetrievalPlanSeed seed) {
        return followUpPlan.tasks().stream()
                .map(task -> {
                    SubQuestionIntent original = seed.intentByTaskId().get(task.taskId());
                    return original == null
                            ? null
                            : new SubQuestionIntent(task.question(), original.nodeScores());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
