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
import java.util.List;
import java.util.Set;

@Component
public class EvidenceEvaluationParser {

    private final Gson gson = new Gson();

    public EvidenceEvaluation parse(String raw, RetrievalPlan plan) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("evidence evaluation response is empty");
        }
        String json = unwrapCodeFence(raw.trim());
        Payload payload;
        try {
            payload = gson.fromJson(json, Payload.class);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("evidence evaluation is not valid JSON", exception);
        }
        if (payload == null || payload.assessments == null || payload.gaps == null
                || payload.confidence < 0 || payload.confidence > 1) {
            throw new IllegalArgumentException("evidence evaluation fields are invalid");
        }
        Set<String> knownTaskIds = new HashSet<>();
        plan.tasks().forEach(task -> knownTaskIds.add(task.taskId()));
        List<TaskAssessment> assessments = payload.assessments.stream()
                .map(item -> toAssessment(item, knownTaskIds))
                .toList();
        if (assessments.size() != knownTaskIds.size()
                || assessments.stream().map(TaskAssessment::taskId).distinct().count()
                        != knownTaskIds.size()) {
            throw new IllegalArgumentException("every retrieval task must be assessed exactly once");
        }
        List<RetrievalGap> gaps = payload.gaps.stream()
                .map(item -> toGap(item, knownTaskIds))
                .toList();
        return new EvidenceEvaluation(
                payload.sufficient,
                assessments,
                gaps,
                payload.confidence,
                payload.explanation);
    }

    private TaskAssessment toAssessment(AssessmentPayload item, Set<String> knownTaskIds) {
        requireKnownTask(item == null ? null : item.taskId, knownTaskIds);
        TaskEvidenceStatus status;
        try {
            status = TaskEvidenceStatus.valueOf(item.status);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("unknown evidence status", exception);
        }
        return new TaskAssessment(
                item.taskId,
                status,
                copySet(item.coveredFacts),
                copySet(item.missingFacts),
                item.explanation);
    }

    private RetrievalGap toGap(GapPayload item, Set<String> knownTaskIds) {
        requireKnownTask(item == null ? null : item.taskId, knownTaskIds);
        return new RetrievalGap(item.taskId, copySet(item.missingFacts), item.suggestedQuery);
    }

    private void requireKnownTask(String taskId, Set<String> knownTaskIds) {
        if (taskId == null || !knownTaskIds.contains(taskId)) {
            throw new IllegalArgumentException("evaluation references unknown task: " + taskId);
        }
    }

    private Set<String> copySet(List<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
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
        private boolean sufficient;
        private List<AssessmentPayload> assessments;
        private List<GapPayload> gaps;
        private double confidence;
        private String explanation;
    }

    private static final class AssessmentPayload {
        private String taskId;
        private String status;
        private List<String> coveredFacts;
        private List<String> missingFacts;
        private String explanation;
    }

    private static final class GapPayload {
        private String taskId;
        private List<String> missingFacts;
        private String suggestedQuery;
    }
}
