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

public record EvidenceItem(
        String taskId,
        String chunkId,
        String documentId,
        String knowledgeBaseId,
        String content,
        double score,
        String sourceTitle,
        String sourceUri,
        int iteration) {

    public EvidenceItem {
        if (taskId == null || taskId.isBlank() || chunkId == null || chunkId.isBlank()) {
            throw new IllegalArgumentException("taskId and chunkId are required");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId is required");
        }
        if (iteration < 1) {
            throw new IllegalArgumentException("iteration must be positive");
        }
    }

    public String deduplicationKey() {
        return documentId + ":" + chunkId;
    }
}
