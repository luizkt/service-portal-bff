package com.serviceportal.bff.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private SecurityConfig newConfig() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "jwksUri",
                "http://localhost:9000/application/o/service-portal/jwks/");
        ReflectionTestUtils.setField(config, "issuerUri",
                "http://localhost:9000/application/o/service-portal/");
        return config;
    }

    @Test @DisplayName("jwtDecoder() cria NimbusJwtDecoder configurado para o issuer")
    void jwtDecoderCriaNimbus() {
        JwtDecoder decoder = newConfig().jwtDecoder();
        assertThat(decoder).isInstanceOf(NimbusJwtDecoder.class);
    }

    @Test @DisplayName("corsConfigurationSource registra /bff/** com métodos esperados")
    void corsConfigSource() {
        CorsConfigurationSource source = newConfig().corsConfigurationSource();
        assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);

        UrlBasedCorsConfigurationSource urlSource = (UrlBasedCorsConfigurationSource) source;
        CorsConfiguration cfg = urlSource.getCorsConfigurations().get("/bff/**");
        assertThat(cfg).isNotNull();
        assertThat(cfg.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(cfg.getAllowedOriginPatterns()).contains("*");
        assertThat(cfg.getAllowedHeaders()).contains("*");
        assertThat(cfg.getMaxAge()).isEqualTo(3600L);
    }
}
