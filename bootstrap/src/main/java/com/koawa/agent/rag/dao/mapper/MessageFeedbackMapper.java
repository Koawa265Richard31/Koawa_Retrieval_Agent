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

package com.koawa.agent.rag.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.koawa.agent.rag.controller.vo.MessageFeedbackVO;
import com.koawa.agent.rag.dao.entity.MessageFeedbackDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MessageFeedbackMapper extends BaseMapper<MessageFeedbackDO> {

    /**
     * 管理台分页查询反馈（联表取用户名/问题/回答/处理人）
     */
    @Select("""
            <script>
            SELECT f.id,
                   f.message_id,
                   f.conversation_id,
                   f.user_id,
                   u.username,
                   f.vote,
                   f.reason,
                   f.comment,
                   f.handled,
                   f.handle_note,
                   f.handle_time,
                   f.handler_id,
                   h.username AS handler_name,
                   msg.content AS answer,
                   q.content AS question,
                   f.create_time,
                   f.update_time
            FROM t_message_feedback f
            LEFT JOIN t_user u ON u.id = f.user_id AND u.deleted = 0
            LEFT JOIN t_user h ON h.id = f.handler_id AND h.deleted = 0
            LEFT JOIN t_message msg ON msg.id = f.message_id AND msg.deleted = 0
            LEFT JOIN LATERAL (
                SELECT m.content
                FROM t_message m
                WHERE m.conversation_id = f.conversation_id
                  AND m.role = 'user'
                  AND m.deleted = 0
                  AND (msg.create_time IS NULL OR m.create_time &lt;= msg.create_time)
                ORDER BY m.create_time DESC
                LIMIT 1
            ) q ON TRUE
            WHERE f.deleted = 0
            <if test="vote != null"> AND f.vote = #{vote}</if>
            <if test="handled != null"> AND f.handled = #{handled}</if>
            <if test="keyword != null and keyword != ''">
                AND (f.comment ILIKE CONCAT('%', #{keyword}, '%')
                  OR f.reason ILIKE CONCAT('%', #{keyword}, '%')
                  OR u.username ILIKE CONCAT('%', #{keyword}, '%')
                  OR msg.content ILIKE CONCAT('%', #{keyword}, '%')
                  OR q.content ILIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY f.create_time DESC
            </script>
            """)
    IPage<MessageFeedbackVO> pageFeedback(Page<?> page,
                                          @Param("vote") Integer vote,
                                          @Param("handled") Integer handled,
                                          @Param("keyword") String keyword);
}
