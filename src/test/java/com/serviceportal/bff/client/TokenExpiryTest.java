/*
 * MIT License
 *
 * Copyright (c) 2026 Luiz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.serviceportal.bff.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenExpiryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    static String jwt(String payloadJson) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))
                + "."
                + enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8))
                + ".sig";
    }

    @Test
    @DisplayName("usa o claim exp do JWT")
    void fromExp() {
        assertThat(TokenExpiry.resolve(jwt("{\"exp\":1767225900}"), 99L, NOW))
                .isEqualTo(Instant.ofEpochSecond(1767225900));
    }

    @Test
    @DisplayName("sem exp usa expiresIn")
    void fromExpiresIn() {
        assertThat(TokenExpiry.resolve("opaco", 120L, NOW)).isEqualTo(NOW.plusSeconds(120));
        assertThat(TokenExpiry.resolve(jwt("{\"sub\":\"a\"}"), 120L, NOW))
                .isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    @DisplayName("sem nada (ou valores inválidos) cai em 3600 s")
    void fallback() {
        assertThat(TokenExpiry.resolve("opaco", null, NOW)).isEqualTo(NOW.plusSeconds(3600));
        assertThat(TokenExpiry.resolve("opaco", 0L, NOW)).isEqualTo(NOW.plusSeconds(3600));
        assertThat(TokenExpiry.resolve(null, null, NOW)).isEqualTo(NOW.plusSeconds(3600));
        assertThat(TokenExpiry.resolve("a.%%%.c", null, NOW)).isEqualTo(NOW.plusSeconds(3600));
        assertThat(TokenExpiry.resolve(jwt("{\"exp\":\"x\"}"), null, NOW))
                .isEqualTo(NOW.plusSeconds(3600));
    }
}
