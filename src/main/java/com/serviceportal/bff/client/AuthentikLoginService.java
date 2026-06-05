package com.serviceportal.bff.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceportal.bff.config.AuthProperties;
import com.serviceportal.bff.dto.AuthentikTokenDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Autentica usuários via Authentik Flow Executor (protocolo multi-step JSON),
 * captura o authorization code e troca por tokens PKCE.
 *
 * Protocolo (seguimento manual de todos os redirects — HTTP/1.1 forçado):
 *  1. GET  /api/v3/flows/executor/service-portal-ropc/   → identification challenge
 *  2. POST uid_field=username                            → 302 (PRG em Docker) ou 200 xak-flow-redirect
 *  3. GET  flow URL (segue o redirect)                  → ak-stage-password
 *  4. POST component=ak-stage-password, password=...     → 302
 *  5. GET  flow URL (segue o redirect)                  → xak-flow-redirect (autenticado)
 *  6. GET  /application/o/authorize/?...&code_challenge  → 302 para redirect_uri com code
 *  7. POST /application/o/token/ com code + code_verifier → tokens
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthentikLoginService {

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    private static final String FLOW_SLUG = "service-portal-ropc";

    // Redirect URI registrado no provider SPA. O BFF não segue este redirect —
    // apenas extrai o code do Location header e usa o mesmo valor na troca de tokens.
    private static final String PKCE_REDIRECT_URI = "http://localhost:5173/auth/callback";

    public AuthentikTokenDto login(String username, String password) throws Exception {
        // Cookie store compartilhado entre todos os requests desta sessão de login
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

        // Um único client: HTTP/1.1 forçado, SEM seguimento automático de redirects.
        // Seguimos todos os 302 manualmente com um GET limpo (sem headers do POST original).
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String base = authProperties.getAuthentikBaseUrl();
        String flowUrl = base + "/api/v3/flows/executor/" + FLOW_SLUG + "/";

        // ── Passo 1: GET → identification stage (estabelece session cookie) ──────────
        apiGet(client, flowUrl);

        // ── Passo 2: POST username ────────────────────────────────────────────────────
        // Authentik pode retornar:
        //   • 200 com xak-flow-redirect (host/desenvolvimento)
        //   • 302 para o mesmo flowUrl (Docker/PRG pattern)
        String identBody = "{\"component\":\"ak-stage-identification\",\"uid_field\":"
                + objectMapper.writeValueAsString(username) + "}";
        HttpResponse<String> identResp = apiPost(client, flowUrl, identBody);

        if (identResp.statusCode() == 302) {
            // PRG: segue manualmente para obter a próxima stage
            String nextUrl = resolveLocation(base, identResp);
            apiGet(client, nextUrl);
        } else {
            // 200: verifica o componente
            String comp = extractComponent(identResp.body());
            if ("ak-stage-identification".equals(comp)) {
                // Voltou ao identification stage — usuário não existe
                throw new BadCredentialsException("Usuário ou senha inválidos");
            }
            if ("xak-flow-redirect".equals(comp)) {
                // Host mode: segue o `to` para chegar ao password stage
                String to = extractFlowTo(identResp.body());
                if (to != null) {
                    apiGet(client, base + to);
                }
            }
            // ak-stage-password: já está no stage correto
        }

        // ── Passo 3: POST password ────────────────────────────────────────────────────
        String passBody = "{\"component\":\"ak-stage-password\",\"password\":"
                + objectMapper.writeValueAsString(password) + "}";
        HttpResponse<String> passResp = apiPost(client, flowUrl, passBody);
        log.debug("Password POST for '{}': status={}", username, passResp.statusCode());

        if (passResp.statusCode() == 302) {
            // Senha aceita → segue redirect para obter o xak-flow-redirect final
            String nextUrl = resolveLocation(base, passResp);
            HttpResponse<String> afterPass = apiGet(client, nextUrl);
            String comp = extractComponent(afterPass.body());
            if (!"xak-flow-redirect".equals(comp)) {
                log.warn("Auth failed for '{}' after password 302: component={}", username, comp);
                throw new BadCredentialsException("Usuário ou senha inválidos");
            }
        } else if (passResp.statusCode() == 200) {
            // 200 com xak-flow-redirect = sucesso sem redirect intermediário
            String comp = extractComponent(passResp.body());
            if (!"xak-flow-redirect".equals(comp)) {
                log.warn("Auth failed for '{}': password stage returned component={}", username, comp);
                throw new BadCredentialsException("Usuário ou senha inválidos");
            }
        } else {
            throw new BadCredentialsException("Usuário ou senha inválidos");
        }

        // ── Passo 4: gera PKCE + chama /authorize (sem seguir o redirect) ────────────
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String state = generateState();

        String authorizeUrl = base + "/application/o/authorize/"
                + "?client_id=" + authProperties.getClientId()
                + "&response_type=code"
                + "&redirect_uri=" + URLEncoder.encode(PKCE_REDIRECT_URI, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(String.join(" ", authProperties.getScopes()), StandardCharsets.UTF_8)
                + "&state=" + state
                + "&code_challenge=" + codeChallenge
                + "&code_challenge_method=S256";

        HttpResponse<String> authResp = apiGet(client, authorizeUrl);

        if (authResp.statusCode() != 302) {
            log.error("Authorize step returned {} for user '{}'", authResp.statusCode(), username);
            throw new IllegalStateException("Authorization step failed (status " + authResp.statusCode() + ")");
        }

        String location = authResp.headers().firstValue("Location")
                .orElseThrow(() -> new IllegalStateException("No Location header in authorize response"));

        String code = extractQueryParam(location, "code");
        if (code == null || code.isEmpty()) {
            String error = extractQueryParam(location, "error");
            throw new IllegalStateException("Authorization failed" + (error != null ? ": " + error : ""));
        }

        // ── Passo 5: troca o code por tokens ─────────────────────────────────────────
        String tokenBody = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(PKCE_REDIRECT_URI, StandardCharsets.UTF_8)
                + "&client_id=" + authProperties.getClientId()
                + "&code_verifier=" + codeVerifier;

        HttpResponse<String> tokenResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/application/o/token/"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(tokenBody)).build(),
                HttpResponse.BodyHandlers.ofString());

        if (tokenResp.statusCode() != 200) {
            log.error("Token exchange failed (status {}): {}", tokenResp.statusCode(), tokenResp.body());
            throw new IllegalStateException("Token exchange failed");
        }

        JsonNode node = objectMapper.readTree(tokenResp.body());
        return AuthentikTokenDto.builder()
                .accessToken(node.path("access_token").asText())
                .idToken(node.path("id_token").asText(null))
                .refreshToken(node.path("refresh_token").asText(null))
                .expiresIn(node.path("expires_in").asLong(3600))
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private HttpResponse<String> apiGet(HttpClient client, String url) throws Exception {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url))
                        .header("Accept", "application/json, text/html, */*").GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> apiPost(HttpClient client, String url, String jsonBody) throws Exception {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String resolveLocation(String base, HttpResponse<?> resp) {
        String loc = resp.headers().firstValue("Location").orElse(null);
        if (loc == null) return base;
        return loc.startsWith("http") ? loc : base + loc;
    }

    private String extractComponent(String body) {
        try {
            return objectMapper.readTree(body).path("component").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractFlowTo(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if ("xak-flow-redirect".equals(node.path("component").asText())) {
                return node.path("to").asText(null);
            }
        } catch (Exception e) {
            log.debug("Could not parse flow executor response: {}", e.getMessage());
        }
        return null;
    }

    private String extractQueryParam(String url, String param) {
        try {
            URI uri = URI.create(url);
            String query = uri.getRawQuery();
            if (query == null) return null;
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && kv[0].equals(param)) {
                    return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract param '{}': {}", param, e.getMessage());
        }
        return null;
    }

    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String verifier) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String generateState() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
