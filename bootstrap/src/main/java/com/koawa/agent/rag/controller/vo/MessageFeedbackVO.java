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

/**
 * 会话消息反馈视图对象（管理控制台）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageFeedbackVO {

    private String id;
    private String messageId;
    private String conversationId;
    private String userId;
    private String username;
    private Integer vote;
    private Integer rating;
    private String source;
    private String reason;
    private String comment;

    /**
     * 用户提问（同会话中最近一条用户消息）
     */
    private String question;

    /**
     * 助手回答内容
     */
    private String answer;

    /**
     * 是否已处理：0=未处理 1=已处理
     */
    private Integer handled;

    private String handleNote;
    private Date handleTime;
    private String handlerId;
    private String handlerName;

    /**
     * 关联链路追踪ID（同会话最近一次检索运行）
     */
    private String traceId;
    private Date createTime;
    private Date updateTime;
}

