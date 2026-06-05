package com.serviceportal.bff.client;

import com.serviceportal.bff.config.ManagerProperties;
import com.serviceportal.bff.dto.LoginRequest;
import com.serviceportal.bff.dto.LoginResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

/** Auth server-to-server contra o service-portal-manager. Gêmeo do {@link OrchestratorAuthService}. */
@Slf4j
@Service
public class ManagerAuthService {

    private final WebClient managerWebClient;
    private final ManagerProperties props;

    private String token;
    private Instant tokenExpiry = Instant.EPOCH;

    public ManagerAuthService(@Qualifier("managerWebClient") WebClient managerWebClient,
                               ManagerProperties props) {
        this.managerWebClient = managerWebClient;
        this.props = props;
    }

    public synchronized String getToken() {
        if (Instant.now().isAfter(tokenExpiry.minusSeconds(60))) {
            refreshToken();
        }
        return token;
    }

    private void refreshToken() {
        log.debug("Renovando token do Manager");
        LoginResponse response = managerWebClient.post()
                .uri("/api/auth/tokens")
                .bodyValue(new LoginRequest(props.getUsername(), props.getPassword()))
                .retrieve()
                .bodyToMono(LoginResponse.class)
                .block();
        if (response == null || response.getToken() == null) {
            throw new IllegalStateException("Manager retornou login sem token");
        }
        this.token = response.getToken();
        this.tokenExpiry = Instant.now().plusSeconds(3600);
        log.debug("Token do Manager renovado com sucesso");
    }
}
