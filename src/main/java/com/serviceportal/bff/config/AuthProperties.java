package com.serviceportal.bff.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "bff.auth")
public class AuthProperties {

    /** Issuer público do Authentik (mesmo do claim "iss" dos tokens). */
    private String issuerUri;

    /** Client ID público da SPA cadastrado no Authentik. */
    private String clientId;

    /** Scopes solicitados no /authorize. */
    private List<String> scopes = new ArrayList<>(List.of("openid", "profile", "email"));

    /** URL base interna do Authentik — usada pelo BFF para chamar o Flow Executor server-side. */
    private String authentikBaseUrl = "http://localhost:9000";
}
