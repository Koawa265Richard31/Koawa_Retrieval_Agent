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

package com.koawa.agent.rag.websearch;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 联网搜索结果条目
 */
@Data
@Builder
public class SearchResult {
    private String provider;
    private String title;
    private String url;
    private String snippet;
    private String description;
    /**
     * 网址对应资源的创建/发布时间（如 B站视频发布时间、X 推文时间、网页发布日期），无则 null
     */
    private Instant resourceCreateTime;
}
