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

package com.koawa.agent.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.koawa.agent.framework.convention.Result;
import com.koawa.agent.framework.web.Results;
import com.koawa.agent.rag.controller.request.MessageFeedbackHandleRequest;
import com.koawa.agent.rag.controller.vo.MessageFeedbackCategoryStatVO;
import com.koawa.agent.rag.controller.vo.MessageFeedbackGovernanceVO;
import com.koawa.agent.rag.controller.request.MessageFeedbackPageRequest;
import com.koawa.agent.rag.controller.vo.MessageFeedbackVO;
import com.koawa.agent.rag.service.MessageFeedbackAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 消息反馈管理控制器（管理控制台，仅 admin）
 */
@RestController
@RequiredArgsConstructor
public class MessageFeedbackAdminController {

    private final MessageFeedbackAdminService adminService;

    /**
     * 分页查询反馈列表
     */
    @GetMapping("/message-feedback")
    public Result<IPage<MessageFeedbackVO>> pageQuery(MessageFeedbackPageRequest requestParam) {
        StpUtil.checkRole("admin");
        return Results.success(adminService.pageQuery(requestParam));
    }

    /**
     * 反馈统计
     */
    @GetMapping("/message-feedback/stats")
    public Result<Map<String, Object>> stats() {
        StpUtil.checkRole("admin");
        return Results.success(adminService.stats());
    }

    /**
     * 反馈分类统计（治理视角）
     */
    @GetMapping("/message-feedback/category-stats")
    public Result<List<MessageFeedbackCategoryStatVO>> categoryStats() {
        StpUtil.checkRole("admin");
        return Results.success(adminService.categoryStats());
    }

    /**
     * 待治理清单（按命中文档归集点踩反馈）
     */
    @GetMapping("/message-feedback/governance")
    public Result<List<MessageFeedbackGovernanceVO>> governance(
            @RequestParam(required = false) Integer handled) {
        StpUtil.checkRole("admin");
        return Results.success(adminService.governance(handled));
    }

    /**
     * 标记为已处理
     */
    @PutMapping("/message-feedback/{id}/handle")
    public Result<Void> handle(@PathVariable String id,
                               @RequestBody(required = false) MessageFeedbackHandleRequest request) {
        StpUtil.checkRole("admin");
        adminService.handle(id, request == null ? null : request.getNote());
        return Results.success();
    }

    /**
     * 取消已处理状态
     */
    @PutMapping("/message-feedback/{id}/unhandle")
    public Result<Void> unhandle(@PathVariable String id) {
        StpUtil.checkRole("admin");
        adminService.unhandle(id);
        return Results.success();
    }
}


