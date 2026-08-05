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

package com.koawa.agent.rag.core.prompt;

import com.koawa.agent.framework.convention.RetrievedChunk;
import com.koawa.agent.infra.token.HeuristicTokenCounterService;
import com.koawa.agent.rag.config.ContextFormattingProperties;
import com.koawa.agent.rag.core.intent.IntentNode;
import com.koawa.agent.rag.core.intent.NodeScore;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultContextFormatterTest {

    private final DefaultContextFormatter formatter = new DefaultContextFormatter(
            new PromptTemplateLoader(new DefaultResourceLoader()),
            new HeuristicTokenCounterService(),
            new ContextFormattingProperties()
    );

    @Test
    void shouldPromoteMarkdownImagesIntoKbContext() {
        String image = "![花海咲季](https://example.com/saki.png)";
        RetrievedChunk chunk = RetrievedChunk.builder()
                .id("chunk-1")
                .text("花海咲季是角色资料。\n" + image)
                .build();
        IntentNode node = IntentNode.builder()
                .id("gakumas-characters-gamekee")
                .promptSnippet("角色资料回答时保留图片。")
                .build();

        String context = formatter.formatKbContext(
                List.of(NodeScore.builder().node(node).score(0.9).build()),
                Map.of("gakumas-characters-gamekee", List.of(chunk)),
                3
        );

        assertTrue(context.contains("<image-markdown>"));
        assertTrue(context.contains("必须保留相关 Markdown 图片"));
        assertEquals(1, countOccurrences(context, image));
    }

    @Test
    void shouldKeepPlainContextWhenNoMarkdownImageExists() {
        RetrievedChunk chunk = RetrievedChunk.builder()
                .id("chunk-1")
                .text("花海咲季是角色资料。")
                .build();

        String context = formatter.formatKbContext(
                List.of(),
                Map.of("fallback", List.of(chunk)),
                3
        );

        assertTrue(context.contains("花海咲季是角色资料。"));
        assertEquals(0, countOccurrences(context, "<image-markdown>"));
    }

    @Test
    void shouldKeepWholeChunksWithinTheTokenBudget() {
        ContextFormattingProperties properties = new ContextFormattingProperties();
        properties.setMaxTokens(10);
        DefaultContextFormatter budgetedFormatter = new DefaultContextFormatter(
                new PromptTemplateLoader(new DefaultResourceLoader()),
                new HeuristicTokenCounterService(),
                properties
        );
        RetrievedChunk fitting = RetrievedChunk.builder().id("card").text("短卡片资料").build();
        RetrievedChunk overflowing = RetrievedChunk.builder().id("guide")
                .text("这是一段很长的攻略内容，用于确认格式化阶段不会截断半个语义片段。")
                .build();

        String context = budgetedFormatter.formatKbContext(
                List.of(), Map.of("fallback", List.of(fitting, overflowing)), 3);

        assertTrue(context.contains("短卡片资料"));
        assertTrue(!context.contains("很长的攻略"));
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int fromIndex = 0;
        while (true) {
            int index = text.indexOf(needle, fromIndex);
            if (index < 0) {
                return count;
            }
            count++;
            fromIndex = index + needle.length();
        }
    }
}
