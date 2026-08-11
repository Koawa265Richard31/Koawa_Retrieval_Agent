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

import java.util.Map;

/**
 * 外部 Agent 对接服务（v2 RetrievalAdapter）
 * <p>
 * 提供异步检索任务的提交、状态查询、结果拉取与取消，
 * 供外部编排器（v2 task_orchestrator）以 REST 方式调用。
 */
public interface ExternalTaskService {

    /**
     * 提交异步检索任务
     *
     * @param query  检索问题/任务描述
     * @param params 额外参数（如 collectionName），可为 null
     * @return 任务 ID
     */
    String submit(String query, Map<String, Object> params);

    /**
     * 查询任务状态
     *
     * @param taskId 任务 ID
     * @return working / completed / failed / canceled，任务不存在返回 null
     */
    String getStatus(String taskId);

    /**
     * 拉取任务结果内容
     *
     * @param taskId 任务 ID
     * @return 结果文本，任务不存在返回 null
     */
    String getResult(String taskId);

    /**
     * 取消任务（尽力而为）
     *
     * @param taskId 任务 ID
     * @return 任务存在且取消成功返回 true
     */
    boolean cancel(String taskId);
}
