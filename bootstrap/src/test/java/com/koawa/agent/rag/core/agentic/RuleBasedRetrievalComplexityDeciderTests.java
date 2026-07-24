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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedRetrievalComplexityDeciderTests {

    private final RetrievalComplexityDecider decider =
            new RuleBasedRetrievalComplexityDecider();

    @Test
    void singleQuestionRemainsSimple() {
        RetrievalComplexityDecision decision = decider.decide(
                new RewriteResult("退款规则是什么", List.of("退款规则是什么")),
                List.of(new SubQuestionIntent("退款规则是什么", List.of())));

        assertFalse(decision.complex());
    }

    @Test
    void multipleRewrittenQuestionsAreComplex() {
        RetrievalComplexityDecision decision = decider.decide(
                new RewriteResult("比较两个版本", List.of("A 有什么", "B 有什么")),
                List.of());

        assertTrue(decision.complex());
        assertTrue(decision.reasons().contains("multiple_rewritten_questions"));
    }

    @Test
    void multipleResolvedIntentsAreComplex() {
        RetrievalComplexityDecision decision = decider.decide(
                new RewriteResult("复合问题", List.of("复合问题")),
                List.of(
                        new SubQuestionIntent("问题一", List.of()),
                        new SubQuestionIntent("问题二", List.of())));

        assertTrue(decision.complex());
    }
}
