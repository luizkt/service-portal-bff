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
package com.serviceportal.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

    @Test
    @DisplayName("Defaults: scopes contém openid, profile, email")
    void defaultScopes() {
        AuthProperties props = new AuthProperties();
        assertThat(props.getScopes()).containsExactly("openid", "profile", "email");
        assertThat(props.getIssuerUri()).isNull();
        assertThat(props.getClientId()).isNull();
    }

    @Test
    @DisplayName("Setters atualizam todos os campos")
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
