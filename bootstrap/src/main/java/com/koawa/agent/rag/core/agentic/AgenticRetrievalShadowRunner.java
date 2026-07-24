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

import com.koawa.agent.framework.trace.RagTraceNode;
import com.koawa.agent.rag.dto.RetrievalContext;
import com.koawa.agent.rag.dto.SubQuestionIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgenticRetrievalShadowRunner {

    private final AgenticRetrievalOrchestrator orchestrator;

    @RagTraceNode(name = "agentic-retrieval-shadow", type = "EVIDENCE_EVALUATION")
    public void evaluate(
            String taskId,
            List<SubQuestionIntent> subIntents,
            RetrievalContext context,
            int topK) {
        AgenticRetrievalResult result = orchestrator.execute(
                taskId, subIntents, context, topK);
        Map<String, TaskEvidenceStatus> taskStatuses = new LinkedHashMap<>();
        result.evidenceLedger().taskStates().forEach(
                (id, state) -> taskStatuses.put(id, state.status()));
        log.info(
                "Agentic Retrieval shadow completed: sufficient={}, stopReason={}, iterations={}, "
                        + "taskStatuses={}, evidence={}",
                result.sufficient(),
                result.stopReason(),
                result.iterationCount(),
                taskStatuses,
                result.evidenceLedger().evidence().size());
    }
}
