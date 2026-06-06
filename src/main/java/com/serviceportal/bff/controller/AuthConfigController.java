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

import com.serviceportal.bff.config.AuthProperties;
import com.serviceportal.bff.dto.AuthConfigDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Auth")
@RestController
@RequestMapping("/bff/auth")
@RequiredArgsConstructor
public class AuthConfigController {

    private final AuthProperties authProperties;

    @Operation(
            summary = "Configuração OAuth2/PKCE",
            description =
                    "Retorna issuer, client_id e scopes para o frontend configurar o fluxo PKCE"
                            + " sem hardcoded no bundle.")
    @ApiResponse(
            responseCode = "200",
            description = "Configuração do provider OAuth2",
            content =
                    @Content(
                            examples =
                                    @ExampleObject(
                                            value =
                                                    "{\"issuerUri\":\"http://localhost:9000/application/o/service-portal/\","
                                                            + "\"clientId\":\"service-portal-spa\","
                                                            + "\"scopes\":[\"openid\",\"profile\",\"email\",\"groups\"]}")))
    @GetMapping("/config")
    public ResponseEntity<AuthConfigDto> config() {
        return ResponseEntity.ok(
                AuthConfigDto.builder()
                        .issuerUri(authProperties.getIssuerUri())
                        .clientId(authProperties.getClientId())
                        .scopes(authProperties.getScopes())
                        .build());
    }
}
