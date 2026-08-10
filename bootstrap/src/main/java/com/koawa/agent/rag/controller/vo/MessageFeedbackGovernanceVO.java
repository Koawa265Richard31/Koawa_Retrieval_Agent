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

package com.koawa.agent.rag.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 反馈治理视图对象：按检索命中文档归集点踩反馈（待治理清单）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageFeedbackGovernanceVO {

    /**
     * 文档ID
     */
    private String docId;

    /**
     * 文档名称
     */
    private String docName;

    /**
     * 所属知识库ID
     */
    private String kbId;

    /**
     * 文档源类型（file/url等）
     */
    private String sourceType;

    /**
     * 源地址
     */
    private String sourceLocation;

    /**
     * 源内容ID（从文档名解析，如 698272；社区自建档为 900003 等）
     */
    private String contentId;

    /**
     * 是否可定点重采（GameKee 源站内容，非社区自建档）
     */
    private Boolean reCrawlable;

    /**
     * 关联点踩反馈数
     */
    private Long dislikeCount;

    /**
     * 其中未处理数
     */
    private Long unhandledCount;

    /**
     * 最近一次点踩时间
     */
    private Date recentTime;

    /**
     * 涉及问题示例（最多3条）
     */
    private List<String> sampleQuestions;
}

