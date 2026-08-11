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

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.koawa.agent.infra.chat.StreamCallback;
import com.koawa.agent.rag.service.ChatExecutionMode;
import com.koawa.agent.rag.service.ExternalTaskService;
import com.koawa.agent.rag.service.pipeline.StreamChatContext;
import com.koawa.agent.rag.service.pipeline.StreamChatPipeline;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 外部 Agent 对接服务默认实现
 * <p>
 * 使用内存 ConcurrentHashMap 保存任务状态，后台线程池调用 RAG 管线执行检索，
 * 与 v2 RetrievalAdapter 的 REST 协议（health/tasks/result）对接。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalTaskServiceImpl implements ExternalTaskService {

    private static final String DEFAULT_COLLECTION = "gakumas-gamekee-pilot-v3";
    private static final String EXTERNAL_USER_ID = "external-v2";
    private static final String EXTERNAL_USERNAME = "external";
    private static final long EXECUTE_TIMEOUT_SECONDS = 120L;

    private final StreamChatPipeline chatPipeline;

    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "external-task-worker");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public String submit(String query, Map<String, Object> params) {
        String taskId = IdUtil.getSnowflakeNextIdStr();
        String collectionName = DEFAULT_COLLECTION;
        if (params != null && params.get("collectionName") != null && StrUtil.isNotBlank(String.valueOf(params.get("collectionName")))) {
            collectionName = String.valueOf(params.get("collectionName"));
        }
        TaskState state = new TaskState(taskId, "working", null, System.currentTimeMillis());
        tasks.put(taskId, state);
        final String finalCollectionName = collectionName;
        executor.submit(() -> execute(taskId, query, finalCollectionName));
        return taskId;
    }

    private void execute(String taskId, String query, String collectionName) {
        TaskState state = tasks.get(taskId);
        if (state == null || state.cancelled) {
            return;
        }
        StringBuilder answer = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        StreamCallback callback = new StreamCallback() {
            @Override
            public void onContent(String content) {
                if (content != null) {
                    answer.append(content);
                }
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                failure.set(error);
                latch.countDown();
            }
        };

        try {
            StreamChatContext ctx = StreamChatContext.builder()
                    .question(query)
                    .conversationId(taskId)
                    .taskId(taskId)
                    .deepThinking(false)
                    .executionMode(ChatExecutionMode.RAG)
                    .collectionName(collectionName)
                    .userId(EXTERNAL_USER_ID)
                    .username(EXTERNAL_USERNAME)
                    .userRole("user")
                    .callback(callback)
                    .build();
            chatPipeline.execute(ctx);
            latch.await(EXECUTE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (state.cancelled) {
                state.status = "canceled";
                return;
            }
            if (failure.get() != null) {
                state.status = "failed";
                state.content = "执行失败: " + failure.get().getMessage();
                return;
            }
            state.content = answer.toString();
            state.status = StrUtil.isBlank(state.content) ? "failed" : "completed";
        } catch (Exception e) {
            log.warn("[外部任务] 执行异常 taskId={}", taskId, e);
            state.status = "failed";
            state.content = "执行异常: " + e.getMessage();
        }
    }

    @Override
    public String getStatus(String taskId) {
        TaskState state = tasks.get(taskId);
        return state == null ? null : state.status;
    }

    @Override
    public String getResult(String taskId) {
        TaskState state = tasks.get(taskId);
        return state == null ? null : state.content;
    }

    @Override
    public boolean cancel(String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null) {
            return false;
        }
        state.cancelled = true;
        if ("working".equals(state.status)) {
            state.status = "canceled";
        }
        return true;
    }

    @Getter
    static class TaskState {
        private final String taskId;
        private volatile String status;
        private volatile String content;
        private final long createdAt;
        private volatile boolean cancelled;

        TaskState(String taskId, String status, String content, long createdAt) {
            this.taskId = taskId;
            this.status = status;
            this.content = content;
            this.createdAt = createdAt;
        }
    }
}
