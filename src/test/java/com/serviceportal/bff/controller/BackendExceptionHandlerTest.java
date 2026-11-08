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
package com.serviceportal.bff.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.serviceportal.bff.client.BackendTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class BackendExceptionHandlerTest {

    private static final String SECRET = "SEGREDO-INTERNO-stacktrace";

    @RestController
    static class Thrower {
        @GetMapping("/resp/{code}")
        String resp(@PathVariable int code) {
            throw WebClientResponseException.create(
                    code,
                    "x",
                    HttpHeaders.EMPTY,
                    SECRET.getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
        }

        @GetMapping("/refused")
        String refused() {
            throw req(new ConnectException("Connection refused " + SECRET));
        }

        @GetMapping("/read-timeout")
        String readTimeout() {
            throw req(ReadTimeoutException.INSTANCE);
        }

        @GetMapping("/connect-timeout")
        String connectTimeout() {
            throw req(new io.netty.channel.ConnectTimeoutException("connect timed out"));
        }

        @GetMapping("/timeout")
        String timeout() throws Exception {
            throw new TimeoutException(SECRET);
        }

        @GetMapping("/netty-timeout")
        String nettyTimeout() {
            throw ReadTimeoutException.INSTANCE;
        }

        @GetMapping("/deadline")
        String deadline() {
            throw new BackendTimeoutException(SECRET);
        }

        private static WebClientRequestException req(Throwable cause) {
            return new WebClientRequestException(
                    cause, HttpMethod.GET, URI.create("http://backend/x"), HttpHeaders.EMPTY);
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc =
                MockMvcBuilders.standaloneSetup(new Thrower())
                        .setControllerAdvice(new BackendExceptionHandler())
                        .build();
    }

    private void expect(String path, int status, String error) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(content().string(not(containsString("SEGREDO"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    @DisplayName("401 do backend vira 401 sem repassar o corpo")
    void unauthorized() throws Exception {
        expect("/resp/401", 401, "Unauthorized");
    }

    @Test
    @DisplayName("403 do backend vira 403")
    void forbidden() throws Exception {
        expect("/resp/403", 403, "Forbidden");
    }

    @Test
    @DisplayName("422 do backend mantém o status")
    void unprocessable() throws Exception {
        // Spring Framework 7 usa o nome atual do status 422 (RFC 9110).
        expect("/resp/422", 422, "Unprocessable Content");
    }

    @Test
    @DisplayName("500 do backend vira 502")
    void serverError() throws Exception {
        expect("/resp/500", 502, "Bad Gateway");
    }

    @Test
    @DisplayName("503 do backend vira 502")
    void backendUnavailable() throws Exception {
        expect("/resp/503", 502, "Bad Gateway");
    }

    @Test
    @DisplayName("status 3xx inesperado vira 502")
    void redirect() throws Exception {
        expect("/resp/302", 502, "Bad Gateway");
    }

    @Test
    @DisplayName("conexão recusada vira 503")
    void connectionRefused() throws Exception {
        expect("/refused", 503, "Service Unavailable");
    }

    @Test
    @DisplayName("ReadTimeout embrulhado em WebClientRequestException vira 504")
    void readTimeout() throws Exception {
        expect("/read-timeout", 504, "Gateway Timeout");
    }

    @Test
    @DisplayName("timeout de conexão vira 504")
    void connectTimeout() throws Exception {
        expect("/connect-timeout", 504, "Gateway Timeout");
    }

    @Test
    @DisplayName("TimeoutException, ReadTimeoutException e prazo total viram 504")
    void directTimeouts() throws Exception {
        expect("/timeout", 504, "Gateway Timeout");
        expect("/netty-timeout", 504, "Gateway Timeout");
        expect("/deadline", 504, "Gateway Timeout");
    }
}
