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

package com.koawa.agent.rag.controller.request;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息反馈分页查询请求（管理控制台）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageFeedbackPageRequest extends Page {

    /**
     * 反馈值：1=点赞，-1=点踩，为空不过滤
     */
    private Integer vote;
    /**
     * 满意度星级：1-5，为空不过滤
     */
    private Integer rating;

    /**
     * 处理状态：0=未处理，1=已处理，为空不过滤
     */
    private Integer handled;

    /**
     * 问题类型/原因（固定分类，精确匹配）
     */
    private String reason;

    /**
     * 关键词（匹配用户名/问题/回答/反馈原因/补充说明）
     */
    private String keyword;
}

