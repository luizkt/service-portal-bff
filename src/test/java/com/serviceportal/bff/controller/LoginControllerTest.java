package com.serviceportal.bff.controller;

import com.serviceportal.bff.client.AuthentikLoginService;
import com.serviceportal.bff.dto.AuthentikTokenDto;
import com.serviceportal.bff.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginControllerTest {

    private AuthentikLoginService loginService;
    private LoginController controller;

    @BeforeEach
    void setUp() {
        loginService = mock(AuthentikLoginService.class);
        controller = new LoginController(loginService);
    }

    @Test @DisplayName("POST /bff/auth/login retorna 200 com tokens em caso de sucesso")
    void loginComSucesso() throws Exception {
        AuthentikTokenDto expected = AuthentikTokenDto.builder()
                .accessToken("AT").idToken("IT").refreshToken("RT").expiresIn(3600).build();
        when(loginService.login("it", "itadmin")).thenReturn(expected);

        ResponseEntity<?> resp = controller.login(new LoginRequest("it", "itadmin"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(expected);
    }

    @Test @DisplayName("POST /bff/auth/login retorna 401 quando credenciais inválidas")
    void loginCredenciaisInvalidas() throws Exception {
        when(loginService.login("user", "wrong")).thenThrow(new BadCredentialsException("Usuário ou senha inválidos"));

        ResponseEntity<?> resp = controller.login(new LoginRequest("user", "wrong"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsKey("error");
    }

    @Test @DisplayName("POST /bff/auth/login retorna 500 quando o serviço lança exceção genérica")
    void loginErroInterno() throws Exception {
        when(loginService.login("user", "pass")).thenThrow(new RuntimeException("connection timeout"));

        ResponseEntity<?> resp = controller.login(new LoginRequest("user", "pass"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
