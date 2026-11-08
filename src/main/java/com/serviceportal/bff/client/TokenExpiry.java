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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Base64;

/**
 * Descobre até quando um token de backend vale. Ordem: claim {@code exp} do JWT (decodificado sem
 * verificar assinatura, pois só dirige o cache), campo {@code expiresIn} da resposta de login e,
 * por fim, 3600 s.
 */
final class TokenExpiry {

    static final long DEFAULT_TTL_SECONDS = 3600;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TokenExpiry() {}

    static Instant resolve(String token, Long expiresInSeconds, Instant now) {
        Instant exp = fromJwt(token);
        if (exp != null) {
            return exp;
        }
        if (expiresInSeconds != null && expiresInSeconds > 0) {
            return now.plusSeconds(expiresInSeconds);
        }
        return now.plusSeconds(DEFAULT_TTL_SECONDS);
    }

    private static Instant fromJwt(String token) {
        if (token == null) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            JsonNode payload = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode exp = payload.get("exp");
            return exp != null && exp.canConvertToLong()
                    ? Instant.ofEpochSecond(exp.asLong())
                    : null;
        } catch (Exception e) {
            return null;
        }
    }
}
