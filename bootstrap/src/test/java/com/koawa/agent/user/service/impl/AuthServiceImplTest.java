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

import com.koawa.agent.framework.exception.ClientException;
import com.koawa.agent.user.controller.request.LoginRequest;
import com.koawa.agent.user.controller.request.RegisterRequest;
import com.koawa.agent.user.controller.vo.LoginVO;
import com.koawa.agent.user.dao.entity.UserDO;
import com.koawa.agent.user.dao.mapper.UserMapper;
import com.koawa.agent.user.service.AuthSessionService;
import com.koawa.agent.user.service.UserPasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private AuthSessionService authSessionService;

    private UserPasswordService userPasswordService;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        userPasswordService = new UserPasswordService();
        service = new AuthServiceImpl(userMapper, userPasswordService, authSessionService);
    }

    @Test
    void shouldRegisterNormalUserWithEncodedPasswordAndLogin() {
        RegisterRequest request = registerRequest("new_user", "Password123", "Password123");
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(UserDO.class))).thenAnswer(invocation -> {
            UserDO user = invocation.getArgument(0);
            user.setId("1001");
            return 1;
        });
        when(authSessionService.login("1001")).thenReturn("token-1");

        LoginVO result = service.register(request);

        assertEquals("1001", result.getUserId());
        assertEquals("user", result.getRole());
        assertEquals("token-1", result.getToken());

        ArgumentCaptor<UserDO> userCaptor = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).insert(userCaptor.capture());
        UserDO inserted = userCaptor.getValue();
        assertEquals("new_user", inserted.getUsername());
        assertEquals("user", inserted.getRole());
        assertFalse("Password123".equals(inserted.getPassword()));
        assertTrue(userPasswordService.matches("Password123", inserted.getPassword()));
    }

    @Test
    void shouldRejectDuplicateUsername() {
        when(userMapper.selectOne(any())).thenReturn(UserDO.builder().id("1").username("existing").build());

        ClientException exception = assertThrows(
                ClientException.class,
                () -> service.register(registerRequest("existing", "Password123", "Password123"))
        );

        assertEquals("用户名已存在", exception.getErrorMessage());
        verify(userMapper, never()).insert(any(UserDO.class));
    }

    @Test
    void shouldRejectMismatchedPasswordConfirmation() {
        ClientException exception = assertThrows(
                ClientException.class,
                () -> service.register(registerRequest("new_user", "Password123", "Password456"))
        );

        assertEquals("两次输入的密码不一致", exception.getErrorMessage());
        verify(userMapper, never()).insert(any(UserDO.class));
    }

    @Test
    void shouldUpgradeLegacyPasswordAfterSuccessfulLogin() {
        UserDO legacyUser = UserDO.builder()
                .id("1001")
                .username("legacy")
                .password("Password123")
                .role("user")
                .build();
        when(userMapper.selectOne(any())).thenReturn(legacyUser);
        when(authSessionService.login("1001")).thenReturn("token-1");
        LoginRequest request = new LoginRequest();
        request.setUsername("legacy");
        request.setPassword("Password123");

        service.login(request);

        verify(userMapper).updateById(legacyUser);
        assertTrue(userPasswordService.isEncoded(legacyUser.getPassword()));
        assertTrue(userPasswordService.matches("Password123", legacyUser.getPassword()));
    }

    private RegisterRequest registerRequest(String username, String password, String confirmPassword) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);
        return request;
    }
}
