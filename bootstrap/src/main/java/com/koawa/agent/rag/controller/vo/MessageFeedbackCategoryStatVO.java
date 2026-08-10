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
 * 消息反馈分类统计视图对象（管理控制台治理视角）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageFeedbackCategoryStatVO {

    /**
     * 反馈原因/问题类型（固定分类，未填写时归为"未填写"）
     */
    private String reason;

    /**
     * 点踩数
     */
    private Long dislikeCount;

    /**
     * 点赞数
     */
    private Long likeCount;

    /**
     * 该分类总反馈数
     */
    private Long totalCount;

    /**
     * 未处理点踩数
     */
    private Long unhandledCount;

    /**
     * 最近一条反馈时间
     */
    private Date lastTime;
}
