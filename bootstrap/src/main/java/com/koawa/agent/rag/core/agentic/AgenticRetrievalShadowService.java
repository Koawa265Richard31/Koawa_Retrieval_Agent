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

import com.koawa.agent.rag.config.AgenticRetrievalProperties;
import com.koawa.agent.rag.dto.RetrievalContext;
import com.koawa.agent.rag.dto.SubQuestionIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class AgenticRetrievalShadowService {

    private final AgenticRetrievalProperties properties;
    private final AgenticRetrievalShadowRunner runner;
    private final Executor shadowExecutor;

    public AgenticRetrievalShadowService(
            AgenticRetrievalProperties properties,
            AgenticRetrievalShadowRunner runner,
            @Qualifier("agenticRetrievalShadowExecutor") Executor shadowExecutor) {
        this.properties = properties;
        this.runner = runner;
        this.shadowExecutor = shadowExecutor;
    }

    public void submit(
            String taskId,
            List<SubQuestionIntent> subIntents,
            RetrievalContext context,
            int topK) {
        if (!properties.isShadowEnabled()) {
            return;
        }
        if (subIntents == null || subIntents.size() < 2) {
            return;
        }
        try {
            shadowExecutor.execute(() -> {
                try {
                    runner.evaluate(taskId, subIntents, context, topK);
                } catch (Exception exception) {
                    log.warn(
                            "Agentic Retrieval shadow evaluation failed; current answer is unchanged: {}",
                            exception.getMessage());
                }
            });
        } catch (RejectedExecutionException exception) {
            log.warn("Agentic Retrieval shadow queue full; evaluation skipped");
        }
    }

}
