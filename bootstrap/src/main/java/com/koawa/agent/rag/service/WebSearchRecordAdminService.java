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
import com.koawa.agent.rag.controller.request.WebSearchRecordPageRequest;
import com.koawa.agent.rag.controller.vo.WebSearchRecordVO;

/**
 * 联网搜索访问记录管理服务（管理控制台，仅 admin）
 */
public interface WebSearchRecordAdminService {

    /**
     * 分页查询联网搜索访问记录
     */
    IPage<WebSearchRecordVO> pageQuery(WebSearchRecordPageRequest request);
}