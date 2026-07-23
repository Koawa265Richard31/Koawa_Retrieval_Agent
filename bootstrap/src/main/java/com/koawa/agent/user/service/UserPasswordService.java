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

package com.koawa.agent.user.service;

import cn.hutool.crypto.digest.BCrypt;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 用户密码编码与兼容校验。
 *
 * <p>新密码统一使用 BCrypt。历史数据可能仍为明文，因此匹配方法暂时兼容明文；
 * 调用方应在历史密码验证成功后立即写回 BCrypt 密文。</p>
 */
@Component
public class UserPasswordService {

    public String encode(String rawPassword) {
        return BCrypt.hashpw(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (!isEncoded(storedPassword)) {
            return Objects.equals(rawPassword, storedPassword);
        }
        try {
            return BCrypt.checkpw(rawPassword, storedPassword);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isEncoded(String password) {
        return password != null
                && (password.startsWith("$2a$")
                || password.startsWith("$2b$")
                || password.startsWith("$2y$"));
    }
}
