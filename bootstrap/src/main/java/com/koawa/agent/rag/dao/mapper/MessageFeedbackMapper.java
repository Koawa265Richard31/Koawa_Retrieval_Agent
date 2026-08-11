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
import com.koawa.agent.rag.controller.vo.MessageFeedbackCategoryStatVO;
import com.koawa.agent.rag.controller.vo.MessageFeedbackVO;
import com.koawa.agent.rag.dao.entity.MessageFeedbackDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MessageFeedbackMapper extends BaseMapper<MessageFeedbackDO> {

    /**
     * 管理台分页查询反馈（联表取用户名/问题/回答/处理人/链路ID）
     */
    @Select("""
            <script>
            SELECT f.id,
                   f.message_id,
                   f.conversation_id,
                   f.user_id,
                   u.username,
                   f.vote,
                   f.rating,
                   f.source,
                   f.reason,
                   f.comment,
                   f.handled,
                   f.handle_note,
                   f.handle_time,
                   f.handler_id,
                   h.username AS handler_name,
                   msg.content AS answer,
                   q.content AS question,
                   (SELECT tr.trace_id
                    FROM t_rag_trace_run tr
                    WHERE tr.conversation_id = f.conversation_id
                      AND tr.user_id = f.user_id
                      AND tr.deleted = 0
                      AND (msg.create_time IS NULL OR tr.start_time &lt;= msg.create_time)
                    ORDER BY tr.start_time DESC
                    LIMIT 1) AS trace_id,
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
            <if test="rating != null"> AND f.rating = #{rating}</if>
            <if test="source != null and source != ''"> AND f.source = #{source}</if>
            <if test="reason != null and reason != ''"> AND f.reason = #{reason}</if>
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
                                          @Param("rating") Integer rating,
                                          @Param("source") String source,
                                          @Param("reason") String reason,
                                          @Param("keyword") String keyword);

    /**
     * 反馈分类统计（治理视角）：按问题类型聚合点赞/点踩/未处理/最近时间
     */
    @Select("""
            SELECT COALESCE(NULLIF(f.reason, ''), '未填写') AS reason,
                   COUNT(*) FILTER (WHERE f.vote = -1)  AS dislike_count,
                   COUNT(*) FILTER (WHERE f.vote = 1)   AS like_count,
                   COUNT(*)                            AS total_count,
                   COUNT(*) FILTER (WHERE f.vote = -1 AND f.handled = 0) AS unhandled_count,
                   MAX(f.create_time)                  AS last_time
            FROM t_message_feedback f
            WHERE f.deleted = 0
            GROUP BY f.reason
            ORDER BY dislike_count DESC, last_time DESC
            """)
    List<MessageFeedbackCategoryStatVO> selectCategoryStats();

    /**
     * 治理归集：查询点踩反馈及其关联链路ID/问题（供按命中文档聚合）
     */
    @Select("""
            <script>
            SELECT f.id,
                   f.reason,
                   f.handled,
                   f.create_time,
                   q.content AS question,
                   tr.trace_id
            FROM t_message_feedback f
            LEFT JOIN LATERAL (
                SELECT m.content
                FROM t_message m
                WHERE m.conversation_id = f.conversation_id
                  AND m.role = 'user'
                  AND m.deleted = 0
                  AND ((SELECT mm.create_time FROM t_message mm WHERE mm.id = f.message_id AND mm.deleted = 0) IS NULL
                       OR m.create_time &lt;= (SELECT mm.create_time FROM t_message mm WHERE mm.id = f.message_id AND mm.deleted = 0))
                ORDER BY m.create_time DESC
                LIMIT 1
            ) q ON TRUE
            LEFT JOIN LATERAL (
                SELECT tr.trace_id
                FROM t_rag_trace_run tr
                WHERE tr.conversation_id = f.conversation_id
                  AND tr.user_id = f.user_id
                  AND tr.deleted = 0
                  AND ((SELECT mm.create_time FROM t_message mm WHERE mm.id = f.message_id AND mm.deleted = 0) IS NULL
                       OR tr.start_time &lt;= (SELECT mm.create_time FROM t_message mm WHERE mm.id = f.message_id AND mm.deleted = 0))
                ORDER BY tr.start_time DESC
                LIMIT 1
            ) tr ON TRUE
            WHERE f.deleted = 0
              AND f.vote = -1
            <if test="handled != null"> AND f.handled = #{handled}</if>
            ORDER BY f.create_time DESC
            </script>
            """)
    List<MessageFeedbackVO> selectGovernanceFeedback(@Param("handled") Integer handled);
}

