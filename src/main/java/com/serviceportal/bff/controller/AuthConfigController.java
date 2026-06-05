package com.serviceportal.bff.controller;

import com.serviceportal.bff.config.AuthProperties;
import com.serviceportal.bff.dto.AuthConfigDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bff/auth")
@RequiredArgsConstructor
public class AuthConfigController {

    private final AuthProperties authProperties;

    /** Endpoint público — usado pelo frontend para configurar o fluxo OAuth2/PKCE
     *  sem hardcoded de issuer/client_id no bundle. */
    @GetMapping("/config")
    public ResponseEntity<AuthConfigDto> config() {
        return ResponseEntity.ok(AuthConfigDto.builder()
                .issuerUri(authProperties.getIssuerUri())
                .clientId(authProperties.getClientId())
                .scopes(authProperties.getScopes())
                .build());
    }
}
