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

package com.koawa.agent.agent.executor.handler;

import com.koawa.agent.agent.domain.AgentAction;
import com.koawa.agent.agent.domain.AgentActionType;
import com.koawa.agent.agent.domain.AgentObservation;
import com.koawa.agent.agent.domain.AgentState;
import com.koawa.agent.agent.executor.AgentActionHandler;
import com.koawa.agent.rag.config.SearchChannelProperties;
import com.koawa.agent.rag.core.intent.IntentResolver;
import com.koawa.agent.rag.core.intent.NodeScoreFilters;
import com.koawa.agent.rag.core.retrieve.RetrievalEngine;
import com.koawa.agent.rag.core.rewrite.RewriteResult;
import com.koawa.agent.rag.dto.RetrievalContext;
import com.koawa.agent.rag.dto.SubQuestionIntent;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RetrieveKbActionHandler implements AgentActionHandler {

    private final IntentResolver intentResolver;
    private final RetrievalEngine retrievalEngine;
    private final SearchChannelProperties searchChannelProperties;

    public RetrieveKbActionHandler(IntentResolver intentResolver,
                                   RetrievalEngine retrievalEngine,
                                   SearchChannelProperties searchChannelProperties) {
        this.intentResolver = Objects.requireNonNull(
                intentResolver,
                "intentResolver cannot be null"
        );
        this.retrievalEngine = Objects.requireNonNull(
                retrievalEngine,
                "retrievalEngine cannot be null"
        );
        this.searchChannelProperties = Objects.requireNonNull(
                searchChannelProperties,
                "searchChannelProperties cannot be null"
        );
    }

    @Override
    public AgentActionType supportedAction() {
        return AgentActionType.RETRIEVE_KB;
    }

    @Override
    public AgentObservation execute(
            AgentAction action,
            AgentState state
    ) {
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(state, "state cannot be null");

        if (action.getType() != AgentActionType.RETRIEVE_KB) {
            throw new IllegalArgumentException(
                    "Unsupported action type: " + action.getType()
            );
        }

        String query = resolveQuery(action);
        int topK = resolveTopK(action);

        RewriteResult rewriteResult = new RewriteResult(
                query,
                List.of(query)
        );

        List<SubQuestionIntent> resolvedIntents =
                intentResolver.resolve(rewriteResult);

        List<SubQuestionIntent> kbSubIntents = resolvedIntents.stream()
                .map(subIntent -> new SubQuestionIntent(
                        subIntent.subQuestion(),
                        NodeScoreFilters.kb(subIntent.nodeScores())
                ))
                .toList();


        RetrievalContext retrievalContext =
                retrievalEngine.retrieve(kbSubIntents, topK);

        String kbContext = Objects.toString(
                retrievalContext.getKbContext(),
                ""
        ).trim();

        return AgentObservation.builder()
                .actionType(AgentActionType.RETRIEVE_KB)
                .success(true)
                .content(kbContext)
                .metadata(Map.of(
                        "query", query,
                        "topK", topK,
                        "empty", kbContext.isBlank()
                ))
                .build();
    }


    private String resolveQuery(AgentAction action) {
        Map<String, Object> arguments = action.getArguments();

        if (arguments == null) {
            throw new IllegalArgumentException(
                    "RETRIEVE_KB arguments cannot be null"
            );
        }

        Object queryValue = arguments.get("query");

        if (!(queryValue instanceof String query) || query.isBlank()) {
            throw new IllegalArgumentException(
                    "RETRIEVE_KB query must be a non-blank string"
            );
        }

        return query.trim();
    }

    private int resolveTopK(AgentAction action) {
        Map<String, Object> arguments = action.getArguments();
        Object topKValue = arguments.get("topK");

        if (topKValue == null) {
            return searchChannelProperties.getDefaultTopK();
        }

        if (!(topKValue instanceof Number number)) {
            throw new IllegalArgumentException(
                    "RETRIEVE_KB topK must be a number"
            );
        }

        int topK = number.intValue();

        if (topK <= 0) {
            throw new IllegalArgumentException(
                    "RETRIEVE_KB topK must be positive"
            );
        }

        return topK;
    }
}
