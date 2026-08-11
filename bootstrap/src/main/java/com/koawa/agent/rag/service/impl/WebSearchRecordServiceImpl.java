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

import com.koawa.agent.rag.dao.entity.WebSearchRecordDO;
import com.koawa.agent.rag.dao.mapper.WebSearchRecordMapper;
import com.koawa.agent.rag.service.WebSearchRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 联网搜索访问记录服务默认实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchRecordServiceImpl implements WebSearchRecordService {

    private final WebSearchRecordMapper webSearchRecordMapper;

    @Override
    public void record(WebSearchRecordDO record) {
        try {
            if (record == null || record.getUrl() == null || record.getUrl().isBlank()) {
                return;
            }
            record.setCreateTime(new Date());
            webSearchRecordMapper.insert(record);
        } catch (Exception e) {
            // 记录失败不影响检索主流程
            log.warn("[web-search] 记录访问失败 url={}", record == null ? null : record.getUrl(), e);
        }
    }
}
