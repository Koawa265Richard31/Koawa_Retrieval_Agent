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

package com.koawa.agent.rag.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.koawa.agent.framework.context.UserContext;
import com.koawa.agent.framework.exception.ClientException;
import com.koawa.agent.rag.controller.request.MessageFeedbackPageRequest;
import com.koawa.agent.rag.controller.vo.MessageFeedbackVO;
import com.koawa.agent.rag.dao.entity.MessageFeedbackDO;
import com.koawa.agent.rag.dao.mapper.MessageFeedbackMapper;
import com.koawa.agent.rag.service.MessageFeedbackAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息反馈管理服务实现
 */
@Service
@RequiredArgsConstructor
public class MessageFeedbackAdminServiceImpl implements MessageFeedbackAdminService {

    private final MessageFeedbackMapper feedbackMapper;

    @Override
    public IPage<MessageFeedbackVO> pageQuery(MessageFeedbackPageRequest request) {
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        Page<?> page = new Page<>(request.getCurrent(), request.getSize());
        return feedbackMapper.pageFeedback(
                page,
                request.getVote(),
                request.getHandled(),
                StrUtil.trimToNull(request.getKeyword())
        );
    }

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> result = new HashMap<>(8);
        long total = feedbackMapper.selectCount(
                Wrappers.lambdaQuery(MessageFeedbackDO.class).eq(MessageFeedbackDO::getDeleted, 0));
        long likeCount = feedbackMapper.selectCount(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getDeleted, 0)
                        .eq(MessageFeedbackDO::getVote, 1));
        long dislikeCount = feedbackMapper.selectCount(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getDeleted, 0)
                        .eq(MessageFeedbackDO::getVote, -1));
        long unhandledCount = feedbackMapper.selectCount(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getDeleted, 0)
                        .eq(MessageFeedbackDO::getHandled, 0));
        long handledCount = feedbackMapper.selectCount(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getDeleted, 0)
                        .eq(MessageFeedbackDO::getHandled, 1));
        long todayCount = feedbackMapper.selectCount(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getDeleted, 0)
                        .ge(MessageFeedbackDO::getCreateTime, DateUtil.beginOfDay(new Date())));
        result.put("total", total);
        result.put("likeCount", likeCount);
        result.put("dislikeCount", dislikeCount);
        result.put("unhandledCount", unhandledCount);
        result.put("handledCount", handledCount);
        result.put("todayCount", todayCount);
        return result;
    }

    @Override
    public void handle(String id, String note) {
        MessageFeedbackDO record = loadById(id);
        String handlerId = UserContext.getUserId();
        feedbackMapper.update(null, Wrappers.lambdaUpdate(MessageFeedbackDO.class)
                .eq(MessageFeedbackDO::getId, record.getId())
                .set(MessageFeedbackDO::getHandled, 1)
                .set(MessageFeedbackDO::getHandleNote, StrUtil.trimToNull(note))
                .set(MessageFeedbackDO::getHandleTime, new Date())
                .set(MessageFeedbackDO::getHandlerId, handlerId));
    }

    @Override
    public void unhandle(String id) {
        MessageFeedbackDO record = loadById(id);
        feedbackMapper.update(null, Wrappers.lambdaUpdate(MessageFeedbackDO.class)
                .eq(MessageFeedbackDO::getId, record.getId())
                .set(MessageFeedbackDO::getHandled, 0)
                .set(MessageFeedbackDO::getHandleNote, null)
                .set(MessageFeedbackDO::getHandleTime, null)
                .set(MessageFeedbackDO::getHandlerId, null));
    }

    private MessageFeedbackDO loadById(String id) {
        MessageFeedbackDO record = feedbackMapper.selectOne(
                Wrappers.lambdaQuery(MessageFeedbackDO.class)
                        .eq(MessageFeedbackDO::getId, id)
                        .eq(MessageFeedbackDO::getDeleted, 0)
        );
        Assert.notNull(record, () -> new ClientException("反馈记录不存在"));
        return record;
    }
}
