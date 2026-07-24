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

import com.koawa.agent.rag.core.rewrite.RewriteResult;
import com.koawa.agent.rag.dto.SubQuestionIntent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuleBasedRetrievalComplexityDecider
        implements RetrievalComplexityDecider {

    private static final int COMPLEX_THRESHOLD = 2;

    @Override
    public RetrievalComplexityDecision decide(
            RewriteResult rewriteResult,
            List<SubQuestionIntent> subIntents) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        int rewrittenParts = rewriteResult == null
                || rewriteResult.subQuestions() == null
                ? 0 : rewriteResult.subQuestions().size();
        int resolvedParts = subIntents == null ? 0 : subIntents.size();

        if (rewrittenParts >= 2) {
            score += 2;
            reasons.add("multiple_rewritten_questions");
        }
        if (resolvedParts >= 2) {
            score += 2;
            reasons.add("multiple_resolved_intents");
        }
        return new RetrievalComplexityDecision(
                score >= COMPLEX_THRESHOLD,
                score,
                List.copyOf(reasons));
    }
}
