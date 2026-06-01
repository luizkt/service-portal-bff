package com.serviceportal.bff.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/** Tokens retornados ao frontend após login via BFF-proxied flow executor. */
@Data
@Builder
public class AuthentikTokenDto {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("id_token")
    private String idToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("expires_in")
    private long expiresIn;

    @JsonProperty("token_type")
    @Builder.Default
    private String tokenType = "Bearer";
}
