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

import com.koawa.agent.rag.service.impl.GameKeeImageProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 图片代理控制器：把知识库中的外部图片地址改写为本站代理地址，
 * 由服务端抓取并缓存到 RustFS，浏览器不再直连外部 CDN。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ImageProxyController {

    private final GameKeeImageProxyService imageProxyService;

    @GetMapping(value = "/rag/v3/img-proxy", produces = MediaType.ALL_VALUE)
    public ResponseEntity<InputStreamResource> proxy(@RequestParam("u") String url) {
        try {
            GameKeeImageProxyService.ImageResult result = imageProxyService.resolve(url);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(result.contentType()))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                    .body(new InputStreamResource(result.inputStream()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.warn("image proxy failed url={}", url, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
