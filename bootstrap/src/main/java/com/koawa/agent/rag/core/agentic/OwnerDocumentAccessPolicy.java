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

import com.koawa.agent.knowledge.dao.entity.KnowledgeBaseDO;
import org.springframework.stereotype.Component;

@Component
public class OwnerDocumentAccessPolicy implements DocumentAccessPolicy {

    @Override
    public boolean canRead(
            RetrievalAccessPrincipal principal,
            KnowledgeBaseDO knowledgeBase) {
        if (principal == null || !principal.identified() || knowledgeBase == null) {
            return false;
        }
        if (principal.administrator()) {
            return true;
        }
        String owner = knowledgeBase.getCreatedBy();
        return owner != null && (owner.equals(principal.username())
                || owner.equals(principal.userId()));
    }
}
