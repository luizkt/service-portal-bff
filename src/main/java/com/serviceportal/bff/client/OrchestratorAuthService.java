package com.serviceportal.bff.client;

import com.serviceportal.bff.config.BffProperties;
import com.serviceportal.bff.dto.LoginRequest;
import com.serviceportal.bff.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorAuthService {

    private final WebClient orchestratorWebClient;
    private final BffProperties props;

    private String token;
    private Instant tokenExpiry = Instant.EPOCH;

    public synchronized String getToken() {
        if (Instant.now().isAfter(tokenExpiry.minusSeconds(60))) {
            refreshToken();
        }
        return token;
    }

    private void refreshToken() {
        log.debug("Renovando token do orquestrador");
        LoginResponse response = orchestratorWebClient.post()
                .uri("/api/auth/login")
                .bodyValue(new LoginRequest(props.getUsername(), props.getPassword()))
                .retrieve()
                .bodyToMono(LoginResponse.class)
                .block();
        this.token = response.getToken();
        this.tokenExpiry = Instant.now().plusSeconds(3600);
        log.debug("Token renovado com sucesso");
    }
}
