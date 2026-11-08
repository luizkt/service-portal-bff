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
package com.serviceportal.bff.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.serviceportal.bff.config.AuthProperties;
import com.serviceportal.bff.dto.AuthentikTokenDto;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

/**
 * Testa o fluxo de login contra um MockWebServer que faz o papel do Authentik (flow executor,
 * /authorize e /token).
 */
class AuthentikLoginServiceTest {

    private static final String FLOW_PATH = "/api/v3/flows/executor/service-portal-ropc/";
    private static final String PASSWORD = "S3cr3t-P4ss!word";
    private static final String USER = "alice";
    private static final String REDIRECT_URI = "http://localhost:5173/auth/callback";
    private static final String BAD = "Usuário ou senha inválidos";

    private MockWebServer server;
    private AuthentikLoginService service;
    private ListAppender<ILoggingEvent> logs;
    private Logger serviceLogger;
    private Level originalLevel;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        AuthProperties props = new AuthProperties();
        props.setClientId("portal-spa");
        props.setAuthentikBaseUrl(base());
        service = new AuthentikLoginService(props, new ObjectMapper());

        serviceLogger = (Logger) LoggerFactory.getLogger(AuthentikLoginService.class);
        originalLevel = serviceLogger.getLevel();
        serviceLogger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() throws IOException {
        serviceLogger.detachAppender(logs);
        serviceLogger.setLevel(originalLevel);
        server.shutdown();
    }

    private String base() {
        String url = server.url("/").toString();
        return url.substring(0, url.length() - 1);
    }

    // ── helpers de resposta ─────────────────────────────────────────────────────

    private static MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static MockResponse component(String component) {
        return json("{\"component\":\"" + component + "\"}");
    }

    private static MockResponse redirect(String location) {
        return new MockResponse().setResponseCode(302).setHeader("Location", location);
    }

    private static MockResponse tokens() {
        return json(
                "{\"access_token\":\"AT\",\"id_token\":\"IT\",\"refresh_token\":\"RT\","
                        + "\"expires_in\":600}");
    }

    /** Passos 1-3 (PRG/Docker) até a senha aceita; deixa o servidor pronto para /authorize. */
    private void enqueueLoginOk() {
        server.enqueue(
                component("ak-stage-identification").addHeader("Set-Cookie", "sid=abc; Path=/"));
        server.enqueue(redirect(FLOW_PATH));
        server.enqueue(component("ak-stage-password"));
        server.enqueue(redirect(FLOW_PATH));
        server.enqueue(component("xak-flow-redirect"));
    }

    private void drain(int n) throws InterruptedException {
        for (int i = 0; i < n; i++) {
            server.takeRequest();
        }
    }

    private static Map<String, String> params(String raw) {
        Map<String, String> map = new HashMap<>();
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            map.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return map;
    }

    private List<String> logLines() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    // ── caminho feliz ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fluxo PRG (302): 7 chamadas em ordem, payloads corretos e tokens retornados")
    void caminhoFelizPrg() throws Exception {
        enqueueLoginOk();
        server.enqueue(redirect(REDIRECT_URI + "?code=CODE%2B1&state=x"));
        server.enqueue(tokens());

        AuthentikTokenDto dto = service.login(USER, PASSWORD);

        assertThat(dto.getAccessToken()).isEqualTo("AT");
        assertThat(dto.getIdToken()).isEqualTo("IT");
        assertThat(dto.getRefreshToken()).isEqualTo("RT");
        assertThat(dto.getExpiresIn()).isEqualTo(600);
        assertThat(dto.getTokenType()).isEqualTo("Bearer");

        RecordedRequest get1 = server.takeRequest();
        assertThat(get1.getMethod()).isEqualTo("GET");
        assertThat(get1.getPath()).isEqualTo(FLOW_PATH);

        RecordedRequest identPost = server.takeRequest();
        assertThat(identPost.getMethod()).isEqualTo("POST");
        assertThat(identPost.getPath()).isEqualTo(FLOW_PATH);
        assertThat(identPost.getHeader("Content-Type")).startsWith("application/json");
        assertThat(identPost.getBody().readUtf8())
                .isEqualTo("{\"component\":\"ak-stage-identification\",\"uid_field\":\"alice\"}");
        // cookie de sessão do passo 1 reenviado
        assertThat(identPost.getHeader("Cookie")).contains("sid=abc");

        assertThat(server.takeRequest().getMethod()).isEqualTo("GET");

        RecordedRequest passPost = server.takeRequest();
        assertThat(passPost.getMethod()).isEqualTo("POST");
        assertThat(passPost.getBody().readUtf8())
                .isEqualTo(
                        "{\"component\":\"ak-stage-password\",\"password\":\"" + PASSWORD + "\"}");

        assertThat(server.takeRequest().getMethod()).isEqualTo("GET");

        RecordedRequest authorize = server.takeRequest();
        assertThat(authorize.getPath()).startsWith("/application/o/authorize/?");
        Map<String, String> aq = params(authorize.getPath().split("\\?", 2)[1]);
        assertThat(aq)
                .containsEntry("client_id", "portal-spa")
                .containsEntry("response_type", "code")
                .containsEntry("redirect_uri", REDIRECT_URI)
                .containsEntry("scope", "openid profile email")
                .containsEntry("code_challenge_method", "S256");

        RecordedRequest token = server.takeRequest();
        assertThat(token.getMethod()).isEqualTo("POST");
        assertThat(token.getPath()).isEqualTo("/application/o/token/");
        assertThat(token.getHeader("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
        Map<String, String> tq = params(token.getBody().readUtf8());
        assertThat(tq)
                .containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "CODE+1") // decodificado do Location e re-encodado
                .containsEntry("redirect_uri", REDIRECT_URI)
                .containsEntry("client_id", "portal-spa");

        // PKCE: challenge = BASE64URL(SHA256(verifier)); state aleatório presente
        String verifier = tq.get("code_verifier");
        String expected =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        assertThat(aq.get("code_challenge")).isEqualTo(expected);
        assertThat(verifier).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(aq.get("state")).hasSize(22).matches("[A-Za-z0-9_-]+");
    }

    @Test
    @DisplayName("PKCE: cada login gera verifier e state novos")
    void pkceDiferenteACadaLogin() throws Exception {
        String[] verifiers = new String[2];
        String[] states = new String[2];
        for (int i = 0; i < 2; i++) {
            enqueueLoginOk();
            server.enqueue(redirect(REDIRECT_URI + "?code=c"));
            server.enqueue(tokens());
            service.login(USER, PASSWORD);
            drain(5);
            states[i] = params(server.takeRequest().getPath().split("\\?", 2)[1]).get("state");
            verifiers[i] = params(server.takeRequest().getBody().readUtf8()).get("code_verifier");
        }
        assertThat(verifiers[0]).isNotEqualTo(verifiers[1]);
        assertThat(states[0]).isNotEqualTo(states[1]);
    }

    @Test
    @DisplayName("Modo host: identification 200 com xak-flow-redirect segue o 'to' e senha 200 ok")
    void caminhoFelizModoHost() throws Exception {
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(json("{\"component\":\"xak-flow-redirect\",\"to\":\"/next/\"}"));
        server.enqueue(component("ak-stage-password")); // GET /next/
        server.enqueue(component("xak-flow-redirect")); // POST password -> 200
        server.enqueue(redirect("/application/o/authorize/done?code=C"));
        server.enqueue(tokens());

        AuthentikTokenDto dto = service.login(USER, PASSWORD);

        assertThat(dto.getAccessToken()).isEqualTo("AT");
        drain(2);
        RecordedRequest follow = server.takeRequest();
        assertThat(follow.getMethod()).isEqualTo("GET");
        assertThat(follow.getPath()).isEqualTo("/next/");
        assertThat(server.takeRequest().getMethod()).isEqualTo("POST");
        assertThat(server.getRequestCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("Identification 200 já no password stage: segue direto para o POST da senha")
    void identificationJaNoPasswordStage() throws Exception {
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(component("ak-stage-password"));
        server.enqueue(component("xak-flow-redirect"));
        server.enqueue(redirect(REDIRECT_URI + "?code=C"));
        server.enqueue(tokens());

        assertThat(service.login(USER, PASSWORD).getAccessToken()).isEqualTo("AT");
        assertThat(server.getRequestCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("xak-flow-redirect sem 'to' não faz GET extra")
    void flowRedirectSemTo() throws Exception {
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(component("xak-flow-redirect"));
        server.enqueue(component("xak-flow-redirect"));
        server.enqueue(redirect(REDIRECT_URI + "?code=C"));
        server.enqueue(tokens());

        service.login(USER, PASSWORD);
        assertThat(server.getRequestCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("302 sem Location no identification volta ao base URL; Location absoluto é usado")
    void locationAusenteEAbsoluto() throws Exception {
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(new MockResponse().setResponseCode(302)); // sem Location -> GET base
        server.enqueue(component("ak-stage-password"));
        server.enqueue(redirect(base() + "/abs/")); // senha aceita, Location absoluto
        server.enqueue(component("xak-flow-redirect"));
        server.enqueue(redirect(REDIRECT_URI + "?code=C"));
        server.enqueue(tokens());

        service.login(USER, PASSWORD);

        drain(2);
        assertThat(server.takeRequest().getPath()).isEqualTo("/");
        server.takeRequest(); // POST senha
        assertThat(server.takeRequest().getPath()).isEqualTo("/abs/");
    }

    @Test
    @DisplayName("Resposta de tokens mínima: opcionais nulos e expires_in padrão 3600")
    void tokensMinimos() throws Exception {
        enqueueLoginOk();
        server.enqueue(redirect(REDIRECT_URI + "?code=C"));
        server.enqueue(json("{\"access_token\":\"only\"}"));

        AuthentikTokenDto dto = service.login(USER, PASSWORD);

        assertThat(dto.getAccessToken()).isEqualTo("only");
        assertThat(dto.getIdToken()).isNull();
        assertThat(dto.getRefreshToken()).isNull();
        assertThat(dto.getExpiresIn()).isEqualTo(3600);
    }

    // ── credenciais inválidas e falhas de cada passo ────────────────────────────

    @Test
    @DisplayName("Usuário inexistente: identification volta ao mesmo stage -> BadCredentials")
    void usuarioInexistente() {
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(component("ak-stage-identification"));

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(BAD);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Senha errada (200 ak-stage-password): BadCredentials e warn sem o usuário")
    void senhaErrada200() {
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(component("ak-stage-password"));
        server.enqueue(component("ak-stage-password")); // senha rejeitada, mesmo stage

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(BAD);
        assertThat(logLines()).anyMatch(l -> l.contains("Auth failed: password stage"));
    }

    @Test
    @DisplayName("Senha errada (302 e próxima stage não é redirect): BadCredentials")
    void senhaErrada302() {
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(redirect(FLOW_PATH));
        server.enqueue(component("ak-stage-password"));
        server.enqueue(redirect(FLOW_PATH));
        server.enqueue(component("ak-stage-password"));

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(BAD);
        assertThat(logLines()).anyMatch(l -> l.contains("Auth failed after password 302"));
    }

    @Test
    @DisplayName("5xx do Authentik no POST da senha vira AuthentikUnavailableException (503)")
    void erro5xxNaSenhaViraIndisponivel() {
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(component("ak-stage-password"));
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(AuthentikUnavailableException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("5xx no identification, no authorize e no token também viram indisponível")
    void erro5xxEmOutrosPassos() {
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(new MockResponse().setResponseCode(502));
        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(AuthentikUnavailableException.class)
                .hasMessageContaining("identification");

        enqueueLoginOk();
        server.enqueue(new MockResponse().setResponseCode(503));
        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(AuthentikUnavailableException.class)
                .hasMessageContaining("authorize");

        enqueueLoginOk();
        server.enqueue(redirect(REDIRECT_URI + "?code=C"));
        server.enqueue(new MockResponse().setResponseCode(500));
        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(AuthentikUnavailableException.class)
                .hasMessageContaining("token");
    }

    @Test
    @DisplayName("Authentik que não responde dentro do request-timeout vira indisponível")
    void timeoutDeRequisicao() {
        server.enqueue(
                component("ak-stage-identification")
                        .setBodyDelay(2, java.util.concurrent.TimeUnit.SECONDS));
        service = serviceWith(props -> props.setRequestTimeout(java.time.Duration.ofMillis(200)));

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(AuthentikUnavailableException.class)
                .hasCauseInstanceOf(java.net.http.HttpTimeoutException.class);
    }

    @Test
    @DisplayName("redirect-uri configurado é usado no /authorize e na troca de tokens")
    void redirectUriConfiguravel() throws Exception {
        String custom = "https://portal.example.com/auth/callback";
        service = serviceWith(props -> props.setRedirectUri(custom));
        enqueueLoginOk();
        server.enqueue(redirect(custom + "?code=C"));
        server.enqueue(tokens());

        service.login(USER, PASSWORD);

        drain(5);
        String authorize = server.takeRequest().getPath();
        String token = server.takeRequest().getBody().readUtf8();
        assertThat(URLDecoder.decode(authorize, StandardCharsets.UTF_8))
                .contains("redirect_uri=" + custom);
        assertThat(URLDecoder.decode(token, StandardCharsets.UTF_8))
                .contains("redirect_uri=" + custom);
    }

    @Test
    @DisplayName("4xx no POST da senha continua virando BadCredentials")
    void status400NaSenha() {
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(component("ak-stage-password"));
        server.enqueue(new MockResponse().setResponseCode(403));

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName(
            "catch #1 (extractComponent): corpo não-JSON no identification vira componente nulo e o fluxo segue")
    void corpoNaoJsonNoIdentificationEngolido() throws Exception {
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("<html>not json</html>"));
        server.enqueue(component("xak-flow-redirect"));
        server.enqueue(redirect(REDIRECT_URI + "?code=C"));
        server.enqueue(tokens());

        // Erro de parse é engolido: o fluxo continua como se estivesse no password stage.
        assertThat(service.login(USER, PASSWORD).getAccessToken()).isEqualTo("AT");
    }

    @Test
    @DisplayName("catch #1 (extractComponent): corpo não-JSON na senha -> BadCredentials")
    void corpoNaoJsonNaSenha() {
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(component("ak-stage-password"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("garbage"));

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName(
            "catch #2 (extractFlowTo): JSON inválido é engolido com log debug (inalcançável pelo fluxo público)")
    void extractFlowToJsonInvalido() throws Exception {
        // No fluxo, o corpo já foi parseado por extractComponent, então este catch só é
        // exercitável por reflexão.
        Method m = AuthentikLoginService.class.getDeclaredMethod("extractFlowTo", String.class);
        m.setAccessible(true);

        assertThat(m.invoke(service, "{not json")).isNull();
        assertThat(m.invoke(service, "{\"component\":\"outro\",\"to\":\"/x\"}")).isNull();
        assertThat(logLines()).anyMatch(l -> l.contains("Could not parse flow executor response"));
    }

    @Test
    @DisplayName(
            "catch #3 (extractQueryParam): Location com URI inválida é engolido -> 'Authorization failed'")
    void locationInvalidaEngolida() {
        enqueueLoginOk();
        server.enqueue(redirect("http://host/cb?code=abc def"));

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authorization failed");
        assertThat(logLines()).anyMatch(l -> l.contains("Could not extract param 'code'"));
    }

    @Test
    @DisplayName("Authorize: status diferente de 302 -> IllegalStateException com o status")
    void authorizeNao302() {
        enqueueLoginOk();
        server.enqueue(new MockResponse().setResponseCode(400));

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authorization step failed (status 400)");
        assertThat(logLines()).anyMatch(l -> l.contains("Authorize step returned 400"));
    }

    @Test
    @DisplayName("Authorize: sem Location -> IllegalStateException")
    void authorizeSemLocation() {
        enqueueLoginOk();
        server.enqueue(new MockResponse().setResponseCode(302));

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No Location header in authorize response");
    }

    @Test
    @DisplayName("Authorize: Location com error= e sem code -> mensagem inclui o erro")
    void authorizeComErro() {
        enqueueLoginOk();
        server.enqueue(redirect(REDIRECT_URI + "?error=access_denied&state=s"));

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authorization failed: access_denied");
    }

    @Test
    @DisplayName("Authorize: Location sem query ou com code vazio -> 'Authorization failed'")
    void authorizeSemCode() {
        enqueueLoginOk();
        server.enqueue(redirect(REDIRECT_URI));
        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authorization failed");

        enqueueLoginOk();
        server.enqueue(redirect(REDIRECT_URI + "?code=&flag&x=1"));
        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authorization failed");
    }

    @Test
    @DisplayName("Token: status != 200 -> IllegalStateException genérica e log com o corpo")
    void tokenFalha() {
        enqueueLoginOk();
        server.enqueue(redirect(REDIRECT_URI + "?code=C"));
        server.enqueue(
                new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_grant\"}"));

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Token exchange failed");
        assertThat(logLines())
                .anyMatch(
                        l ->
                                l.contains("Token exchange failed (status 400)")
                                        && l.contains("invalid_grant"));
    }

    @Test
    @DisplayName("Token: 200 com JSON malformado propaga a exceção do Jackson (sem tradução)")
    void tokenJsonMalformado() {
        enqueueLoginOk();
        server.enqueue(redirect(REDIRECT_URI + "?code=C"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{not json"));

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(tools.jackson.core.JacksonException.class);
    }

    @Test
    @DisplayName("Authentik indisponível (conexão recusada) vira AuthentikUnavailableException")
    void conexaoRecusada() throws IOException {
        server.shutdown();

        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(AuthentikUnavailableException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    // ── logs ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A senha nunca aparece em logs (sucesso e falhas)")
    void senhaNuncaNosLogs() throws Exception {
        // sucesso
        enqueueLoginOk();
        server.enqueue(redirect(REDIRECT_URI + "?code=C"));
        server.enqueue(tokens());
        service.login(USER, PASSWORD);
        // senha rejeitada
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(component("ak-stage-password"));
        server.enqueue(component("ak-stage-password"));
        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(BadCredentialsException.class);
        // 500 no passo da senha
        server.enqueue(component("ak-stage-identification"));
        server.enqueue(component("ak-stage-password"));
        server.enqueue(new MockResponse().setResponseCode(500));
        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(AuthentikUnavailableException.class);
        // authorize falha
        enqueueLoginOk();
        server.enqueue(new MockResponse().setResponseCode(500));
        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(AuthentikUnavailableException.class);
        // token falha
        enqueueLoginOk();
        server.enqueue(redirect(REDIRECT_URI + "?code=C"));
        server.enqueue(new MockResponse().setResponseCode(500).setBody("err"));
        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(AuthentikUnavailableException.class);

        assertThat(logs.list).isNotEmpty();
        for (ILoggingEvent e : logs.list) {
            assertThat(e.getFormattedMessage()).doesNotContain(PASSWORD);
            assertThat(String.valueOf(e.getArgumentArray())).doesNotContain(PASSWORD);
        }
    }

    @Test
    @DisplayName("o username não é escrito em nenhum log, nem no sucesso nem nas falhas")
    void usernameNuncaNosLogs() throws Exception {
        enqueueLoginOk();
        server.enqueue(redirect(REDIRECT_URI + "?code=C"));
        server.enqueue(tokens());
        service.login(USER, PASSWORD);

        server.enqueue(component("ak-stage-identification"));
        server.enqueue(component("ak-stage-password"));
        server.enqueue(component("ak-stage-password"));
        assertThatThrownBy(() -> service.login(USER, PASSWORD))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains(USER));
    }

    private AuthentikLoginService serviceWith(java.util.function.Consumer<AuthProperties> c) {
        AuthProperties props = new AuthProperties();
        props.setClientId("portal-spa");
        props.setAuthentikBaseUrl(base());
        c.accept(props);
        return new AuthentikLoginService(props, new ObjectMapper());
    }
}
