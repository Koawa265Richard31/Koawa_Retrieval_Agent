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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Agent 联网搜索配置
 * <p>博查(Bocha) 为主路（国内稳定、中文社区收录好），Bing(Azure) 为补强（全球/日文）。
 * apiKey 未配置时对应 Provider 自动禁用，整个搜索开关由 enabled 控制（默认关闭，避免越权/浪费额度）。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "rag.web-search")
public class WebSearchProperties {

    /**
     * 联网搜索总开关，默认关闭（Agent 不联网）
     */
    private boolean enabled = false;

    /**
     * 单次搜索最多返回条数（每路）
     */
    private int maxResults = 8;

    /**
     * 搜索请求超时
     */
    private Duration timeout = Duration.ofSeconds(15);

    private Bocha bocha = new Bocha();
    private Bing bing = new Bing();

    @Data
    public static class Bocha {
        private String apiKey = "";
        private String endpoint = "https://api.bochaai.com/v1/web-search";
    }

    @Data
    public static class Bing {
        private String apiKey = "";
        private String endpoint = "https://api.bing.microsoft.com/v7.0/search";
    }
}
