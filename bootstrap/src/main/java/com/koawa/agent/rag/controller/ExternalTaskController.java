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

import cn.hutool.core.util.StrUtil;
import com.koawa.agent.rag.service.ExternalTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 外部 Agent 对接控制器（v2 RetrievalAdapter REST 协议）
 * <p>
 * 端点（nginx 将 /api/health、/api/tasks* 转发到本控制器的 /v2/* 前缀）：
 * - GET    /v2/health        连通性探测
 * - POST   /v2/tasks         提交异步检索任务
 * - GET    /v2/tasks/{id}    查询任务状态
 * - GET    /v2/tasks/{id}/result  拉取执行结果
 * - DELETE /v2/tasks/{id}    取消任务（尽力）
 */
@RestController
@RequestMapping("/v2")
@RequiredArgsConstructor
public class ExternalTaskController {

    private final ExternalTaskService externalTaskService;

    /**
     * 连通性探测
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> body = new HashMap<>();
        body.put("status", "ok");
        return body;
    }

    /**
     * 提交异步检索任务
     */
    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody(required = false) Map<String, Object> request) {
        if (request == null || StrUtil.isBlank(String.valueOf(request.get("query") == null ? "" : request.get("query")))) {
            return ResponseEntity.badRequest().body(Map.of("error", "query 不能为空"));
        }
        String query = String.valueOf(request.get("query"));
        Map<String, Object> params = new HashMap<>(request);
        params.remove("query");
        String taskId = externalTaskService.submit(query, params);
        Map<String, Object> body = new HashMap<>();
        body.put("task_id", taskId);
        return ResponseEntity.ok(body);
    }

    /**
     * 查询任务状态
     */
    @GetMapping("/tasks/{id}")
    public ResponseEntity<Map<String, String>> status(@PathVariable String id) {
        String status = externalTaskService.getStatus(id);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, String> body = new HashMap<>();
        body.put("status", status);
        return ResponseEntity.ok(body);
    }

    /**
     * 拉取任务结果
     */
    @GetMapping("/tasks/{id}/result")
    public ResponseEntity<Map<String, Object>> result(@PathVariable String id) {
        String content = externalTaskService.getResult(id);
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        return ResponseEntity.ok(body);
    }

    /**
     * 取消任务（尽力而为）
     */
    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> cancel(@PathVariable String id) {
        boolean ok = externalTaskService.cancel(id);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
