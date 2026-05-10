package com.serviceportal.bff.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Configuração pública do OAuth2/PKCE entregue ao frontend. */
@Data
@Builder
public class AuthConfigDto {
    /** Issuer público do Authentik (frontend usa para descobrir endpoints OIDC). */
    private String issuerUri;

    /** Client ID público da SPA cadastrado no Authentik. */
    private String clientId;

    /** Scopes que o frontend deve solicitar no /authorize. */
    private List<String> scopes;
}
