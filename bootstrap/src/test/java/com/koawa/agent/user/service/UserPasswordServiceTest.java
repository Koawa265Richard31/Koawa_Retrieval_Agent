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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPasswordServiceTest {

    private final UserPasswordService service = new UserPasswordService();

    @Test
    void shouldEncodeAndMatchPassword() {
        String encoded = service.encode("Password123");

        assertNotEquals("Password123", encoded);
        assertTrue(service.isEncoded(encoded));
        assertTrue(service.matches("Password123", encoded));
        assertFalse(service.matches("wrong-password", encoded));
    }

    @Test
    void shouldTemporarilyMatchLegacyPlaintextPassword() {
        assertTrue(service.matches("legacy-password", "legacy-password"));
        assertFalse(service.matches("wrong-password", "legacy-password"));
    }
}
