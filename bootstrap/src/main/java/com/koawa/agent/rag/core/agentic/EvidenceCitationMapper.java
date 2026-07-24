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

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EvidenceCitationMapper {

    public List<EvidenceCitation> map(List<EvidenceItem> evidence) {
        Map<String, EvidenceCitation> citations = new LinkedHashMap<>();
        if (evidence == null) {
            return List.of();
        }
        for (EvidenceItem item : evidence) {
            if (item == null || item.chunkId() == null || item.documentId() == null) {
                continue;
            }
            String key = item.deduplicationKey();
            citations.computeIfAbsent(key, ignored -> new EvidenceCitation(
                    "E" + (citations.size() + 1),
                    item.chunkId(),
                    item.documentId(),
                    item.sourceTitle(),
                    item.sourceUri()));
        }
        return List.copyOf(new ArrayList<>(citations.values()));
    }

    public boolean referencesExistingCitation(
            String citationId,
            List<EvidenceCitation> citations) {
        return citationId != null && citations != null
                && citations.stream()
                .map(EvidenceCitation::citationId)
                .anyMatch(citationId::equals);
    }
}
