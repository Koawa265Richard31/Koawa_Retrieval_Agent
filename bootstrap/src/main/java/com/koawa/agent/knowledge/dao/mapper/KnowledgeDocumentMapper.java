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

package com.koawa.agent.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.koawa.agent.knowledge.dao.entity.KnowledgeDocumentDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;

public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentDO> {

    /**
     * 查询某知识库下文档的最后更新时间（逻辑未删除的最大 update_time）
     */
    @Select("SELECT MAX(update_time) FROM t_knowledge_document WHERE kb_id = #{kbId} AND deleted = 0")
    Date selectMaxUpdateTime(@Param("kbId") String kbId);

    /**
     * 查询全库文档的最后更新时间（逻辑未删除的最大 update_time）
     */
    @Select("SELECT MAX(update_time) FROM t_knowledge_document WHERE deleted = 0")
    Date selectGlobalMaxUpdateTime();
}
