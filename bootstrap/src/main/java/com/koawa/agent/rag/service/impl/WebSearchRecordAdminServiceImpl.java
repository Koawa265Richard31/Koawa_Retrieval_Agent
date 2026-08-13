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

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.koawa.agent.framework.exception.ClientException;
import com.koawa.agent.rag.controller.request.WebSearchRecordPageRequest;
import com.koawa.agent.rag.controller.vo.WebSearchRecordVO;
import com.koawa.agent.rag.dao.entity.WebSearchRecordDO;
import com.koawa.agent.rag.dao.mapper.WebSearchRecordMapper;
import com.koawa.agent.rag.service.WebSearchRecordAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 联网搜索访问记录管理服务默认实现
 */
@Service
@RequiredArgsConstructor
public class WebSearchRecordAdminServiceImpl implements WebSearchRecordAdminService {

    private final WebSearchRecordMapper webSearchRecordMapper;

    @Override
    public IPage<WebSearchRecordVO> pageQuery(WebSearchRecordPageRequest request) {
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        Page<WebSearchRecordDO> page = new Page<>(request.getCurrent(), request.getSize());
        Page<WebSearchRecordDO> result = webSearchRecordMapper.selectPage(page,
                Wrappers.<WebSearchRecordDO>lambdaQuery()
                        .eq(StrUtil.isNotBlank(request.getProvider()),
                                WebSearchRecordDO::getProvider,
                                StrUtil.trimToNull(request.getProvider()))
                        .and(StrUtil.isNotBlank(request.getKeyword()), wrapper -> wrapper
                                .like(WebSearchRecordDO::getQuestion, request.getKeyword())
                                .or().like(WebSearchRecordDO::getQuery, request.getKeyword())
                                .or().like(WebSearchRecordDO::getUrl, request.getKeyword())
                                .or().like(WebSearchRecordDO::getUrlTitle, request.getKeyword()))
                        .orderByDesc(WebSearchRecordDO::getCreateTime)
                        .orderByDesc(WebSearchRecordDO::getId));
        return result.convert(this::toVO);
    }

    private WebSearchRecordVO toVO(WebSearchRecordDO record) {
        if (record == null) {
            return null;
        }
        return WebSearchRecordVO.builder()
                .id(record.getId())
                .traceId(record.getTraceId())
                .conversationId(record.getConversationId())
                .messageId(record.getMessageId())
                .question(record.getQuestion())
                .provider(record.getProvider())
                .query(record.getQuery())
                .url(record.getUrl())
                .urlTitle(record.getUrlTitle())
                .description(record.getDescription())
                .snippet(record.getSnippet())
                .visitTime(record.getVisitTime())
                .resourceCreateTime(record.getResourceCreateTime())
                .createTime(record.getCreateTime())
                .build();
    }
}