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

import com.serviceportal.bff.config.BffProperties;
import com.serviceportal.bff.dto.LoginRequest;
import com.serviceportal.bff.dto.LoginResponse;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class OrchestratorAuthService {

    private final WebClient orchestratorWebClient;
    private final BffProperties props;

    private String token;
    private Instant tokenExpiry = Instant.EPOCH;
    private final Duration deadline;

    public OrchestratorAuthService(
            @Qualifier("orchestratorWebClient") WebClient orchestratorWebClient,
            BffProperties props) {
        this.orchestratorWebClient = orchestratorWebClient;
        this.props = props;
        this.deadline = HttpTimeouts.deadline(props.getTimeoutMs());
    }

    public synchronized String getToken() {
        if (Instant.now().isAfter(tokenExpiry.minusSeconds(60))) {
            refreshToken();
        }
        return token;
    }

    /**
     * Descarta o token em cache se ele ainda for o que falhou (outra thread pode já ter renovado).
     */
    public synchronized void invalidate(String failedToken) {
        if (failedToken != null && failedToken.equals(token)) {
            log.debug("Token invalidado após 401 do backend");
            tokenExpiry = Instant.EPOCH;
        }
    }

    private void refreshToken() {
        log.debug("Renovando token do orquestrador");
        LoginResponse response =
                orchestratorWebClient
                        .post()
                        .uri("/api/auth/tokens")
                        .bodyValue(new LoginRequest(props.getUsername(), props.getPassword()))
                        .retrieve()
                        .bodyToMono(LoginResponse.class)
                        .timeout(deadline, HttpTimeouts.onTimeout(deadline))
                        .block();
        if (response == null || response.getToken() == null) {
            throw new IllegalStateException("Orquestrador retornou login sem token");
        }
        this.token = response.getToken();
        this.tokenExpiry =
                TokenExpiry.resolve(response.getToken(), response.getExpiresIn(), Instant.now());
        log.debug("Token do orquestrador renovado com sucesso");
    }
}
