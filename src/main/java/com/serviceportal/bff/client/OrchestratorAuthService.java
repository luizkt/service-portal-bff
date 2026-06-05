package com.serviceportal.bff.client;

import com.serviceportal.bff.config.BffProperties;
import com.serviceportal.bff.dto.LoginRequest;
import com.serviceportal.bff.dto.LoginResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

@Slf4j
@Service
public class OrchestratorAuthService {

    private final WebClient orchestratorWebClient;
    private final BffProperties props;

    private String token;
    private Instant tokenExpiry = Instant.EPOCH;

    public OrchestratorAuthService(@Qualifier("orchestratorWebClient") WebClient orchestratorWebClient,
                                    BffProperties props) {
        this.orchestratorWebClient = orchestratorWebClient;
        this.props = props;
    }

    public synchronized String getToken() {
        if (Instant.now().isAfter(tokenExpiry.minusSeconds(60))) {
            refreshToken();
        }
        return token;
    }

    private void refreshToken() {
        log.debug("Renovando token do orquestrador");
        LoginResponse response = orchestratorWebClient.post()
                .uri("/api/auth/tokens")
                .bodyValue(new LoginRequest(props.getUsername(), props.getPassword()))
                .retrieve()
                .bodyToMono(LoginResponse.class)
                .block();
        if (response == null || response.getToken() == null) {
            throw new IllegalStateException("Orquestrador retornou login sem token");
        }
        this.token = response.getToken();
        this.tokenExpiry = Instant.now().plusSeconds(3600);
        log.debug("Token do orquestrador renovado com sucesso");
    }
}
