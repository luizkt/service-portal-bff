package com.serviceportal.bff.controller;

import com.serviceportal.bff.config.AuthProperties;
import com.serviceportal.bff.dto.AuthConfigDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthConfigControllerTest {

    @Test @DisplayName("GET /bff/auth/config retorna issuer, clientId e scopes")
    void retornaConfigCompleta() {
        AuthProperties props = new AuthProperties();
        props.setIssuerUri("https://idp/issuer/");
        props.setClientId("spa");
        props.setScopes(List.of("openid", "profile"));

        AuthConfigController controller = new AuthConfigController(props);
        ResponseEntity<AuthConfigDto> resp = controller.config();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthConfigDto body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getIssuerUri()).isEqualTo("https://idp/issuer/");
        assertThat(body.getClientId()).isEqualTo("spa");
        assertThat(body.getScopes()).containsExactly("openid", "profile");
    }
}
