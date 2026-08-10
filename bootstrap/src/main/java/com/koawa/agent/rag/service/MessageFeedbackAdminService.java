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

package com.koawa.agent.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.koawa.agent.rag.controller.request.MessageFeedbackPageRequest;
import com.koawa.agent.rag.controller.vo.MessageFeedbackCategoryStatVO;
import com.koawa.agent.rag.controller.vo.MessageFeedbackVO;

import java.util.List;
import java.util.Map;

/**
 * 消息反馈管理服务（管理控制台）
 */
public interface MessageFeedbackAdminService {

    /**
     * 分页查询反馈（联表返回用户名/问题/回答/处理人）
     */
    IPage<MessageFeedbackVO> pageQuery(MessageFeedbackPageRequest request);

    /**
     * 反馈统计：总数/点赞数/点踩数/未处理数/已处理数/今日新增
     */
    Map<String, Object> stats();

    /**
     * 反馈分类统计（按问题类型聚合，治理视角）
     */
    List<MessageFeedbackCategoryStatVO> categoryStats();

    /**
     * 标记反馈为已处理
     *
     * @param id   反馈ID
     * @param note 处理备注（可选）
     */
    void handle(String id, String note);

    /**
     * 取消已处理状态
     *
     * @param id 反馈ID
     */
    void unhandle(String id);
}


