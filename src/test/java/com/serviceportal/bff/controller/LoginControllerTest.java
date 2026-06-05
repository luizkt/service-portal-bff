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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.serviceportal.bff.client.AuthentikLoginService;
import com.serviceportal.bff.dto.AuthentikTokenDto;
import com.serviceportal.bff.dto.LoginRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

class LoginControllerTest {

    private AuthentikLoginService loginService;
    private LoginController controller;

    @BeforeEach
    void setUp() {
        loginService = mock(AuthentikLoginService.class);
        controller = new LoginController(loginService);
    }

    @Test
    @DisplayName("POST /bff/auth/login retorna 200 com tokens em caso de sucesso")
    void loginComSucesso() throws Exception {
        AuthentikTokenDto expected =
                AuthentikTokenDto.builder()
                        .accessToken("AT")
                        .idToken("IT")
                        .refreshToken("RT")
                        .expiresIn(3600)
                        .build();
        when(loginService.login("it", "itadmin")).thenReturn(expected);

        ResponseEntity<?> resp = controller.login(new LoginRequest("it", "itadmin"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(expected);
    }

    @Test
    @DisplayName("POST /bff/auth/login retorna 401 quando credenciais inválidas")
    void loginCredenciaisInvalidas() throws Exception {
        when(loginService.login("user", "wrong"))
                .thenThrow(new BadCredentialsException("Usuário ou senha inválidos"));

        ResponseEntity<?> resp = controller.login(new LoginRequest("user", "wrong"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsKey("error");
    }

    @Test
    @DisplayName("POST /bff/auth/login retorna 500 quando o serviço lança exceção genérica")
    void loginErroInterno() throws Exception {
        when(loginService.login("user", "pass"))
                .thenThrow(new RuntimeException("connection timeout"));

        ResponseEntity<?> resp = controller.login(new LoginRequest("user", "pass"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
