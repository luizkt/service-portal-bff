package com.serviceportal.bff.controller;

import com.serviceportal.bff.client.AuthentikLoginService;
import com.serviceportal.bff.dto.AuthentikTokenDto;
import com.serviceportal.bff.dto.LoginRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/bff/auth")
@RequiredArgsConstructor
public class LoginController {

    private final AuthentikLoginService loginService;

    /**
     * Endpoint público — recebe username/senha do frontend e autentica via
     * Authentik Flow Executor server-side. Retorna tokens OAuth2/OIDC prontos para uso.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthentikTokenDto tokens = loginService.login(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(tokens);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Login failed for user '{}': {}", request.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Serviço de login indisponível. Tente novamente."));
        }
    }
}
