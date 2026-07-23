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

package com.koawa.agent.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.koawa.agent.framework.exception.ClientException;
import com.koawa.agent.user.controller.request.LoginRequest;
import com.koawa.agent.user.controller.request.RegisterRequest;
import com.koawa.agent.user.controller.vo.LoginVO;
import com.koawa.agent.user.dao.entity.UserDO;
import com.koawa.agent.user.dao.mapper.UserMapper;
import com.koawa.agent.user.enums.UserRole;
import com.koawa.agent.user.service.AuthService;
import com.koawa.agent.user.service.AuthSessionService;
import com.koawa.agent.user.service.UserPasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_AVATAR_URL = "https://avatars.githubusercontent.com/u/583231?v=4";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final UserMapper userMapper;
    private final UserPasswordService userPasswordService;
    private final AuthSessionService authSessionService;

    @Override
    public LoginVO login(LoginRequest requestParam) {
        if (requestParam == null) {
            throw new ClientException("用户名或密码不能为空");
        }
        String username = requestParam.getUsername();
        String password = requestParam.getPassword();
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            throw new ClientException("用户名或密码不能为空");
        }
        UserDO user = findByUsername(username);
        if (user == null || !userPasswordService.matches(password, user.getPassword())) {
            throw new ClientException("用户名或密码错误");
        }
        if (!userPasswordService.isEncoded(user.getPassword())) {
            user.setPassword(userPasswordService.encode(password));
            userMapper.updateById(user);
        }
        return establishSession(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterRequest requestParam) {
        if (requestParam == null) {
            throw new ClientException("注册信息不能为空");
        }
        String username = StrUtil.trimToNull(requestParam.getUsername());
        String password = requestParam.getPassword();
        String confirmPassword = requestParam.getConfirmPassword();
        validateRegistration(username, password, confirmPassword);

        if (findByUsername(username) != null) {
            throw new ClientException("用户名已存在");
        }

        UserDO user = UserDO.builder()
                .username(username)
                .password(userPasswordService.encode(password))
                .role(UserRole.USER.getCode())
                .build();
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new ClientException("用户名已存在");
        }
        return establishSession(user);
    }

    private LoginVO establishSession(UserDO user) {
        if (user == null || user.getId() == null) {
            throw new ClientException("用户信息异常");
        }
        String loginId = user.getId().toString();
        String token = authSessionService.login(loginId);
        String avatar = StrUtil.isBlank(user.getAvatar()) ? DEFAULT_AVATAR_URL : user.getAvatar();
        return new LoginVO(loginId, user.getRole(), token, avatar);
    }

    @Override
    public void logout() {
        authSessionService.logout();
    }

    private UserDO findByUsername(String username) {
        if (StrUtil.isBlank(username)) {
            return null;
        }
        return userMapper.selectOne(
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getUsername, username)
                        .eq(UserDO::getDeleted, 0)
        );
    }

    private void validateRegistration(String username, String password, String confirmPassword) {
        if (StrUtil.isBlank(username)) {
            throw new ClientException("用户名不能为空");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new ClientException("用户名需为 3-32 位字母、数字、下划线或连字符");
        }
        if (StrUtil.isBlank(password)) {
            throw new ClientException("密码不能为空");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new ClientException("密码长度需为 8-72 位");
        }
        if (!password.equals(confirmPassword)) {
            throw new ClientException("两次输入的密码不一致");
        }
    }
}
