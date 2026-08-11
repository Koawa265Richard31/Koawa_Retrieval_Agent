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

package com.koawa.agent.rag.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Agent 联网搜索访问记录实体：网址与原始问题绑定，含访问时间/资源创建时间/描述
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_web_search_record")
public class WebSearchRecordDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String traceId;
    private String conversationId;
    private String messageId;

    /**
     * 原始问题（绑定）
     */
    private String question;

    /**
     * 搜索提供方：bocha / bing
     */
    private String provider;

    /**
     * 搜索查询
     */
    private String query;

    /**
     * 访问过的网址
     */
    private String url;

    private String urlTitle;

    /**
     * 网址对应内容描述
     */
    private String description;

    private String snippet;

    /**
     * 访问时间
     */
    private Date visitTime;

    /**
     * 网址对应资源的创建时间
     */
    private Date resourceCreateTime;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableLogic
    private Integer deleted;
}
