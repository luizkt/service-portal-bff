package com.serviceportal.bff.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPropertiesTest {

    @Test @DisplayName("Defaults: scopes contém openid, profile, email")
    void defaultScopes() {
        AuthProperties props = new AuthProperties();
        assertThat(props.getScopes()).containsExactly("openid", "profile", "email");
        assertThat(props.getIssuerUri()).isNull();
        assertThat(props.getClientId()).isNull();
    }

    @Test @DisplayName("Setters atualizam todos os campos")
    void settersAtualizamCampos() {
        AuthProperties props = new AuthProperties();
        props.setIssuerUri("https://idp/issuer/");
        props.setClientId("spa-client");
        props.setScopes(List.of("openid", "groups"));

        assertThat(props.getIssuerUri()).isEqualTo("https://idp/issuer/");
        assertThat(props.getClientId()).isEqualTo("spa-client");
        assertThat(props.getScopes()).containsExactly("openid", "groups");
    }
}
