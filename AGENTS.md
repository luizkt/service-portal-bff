# AGENTS.md — service-portal-bff

Índice para agentes de IA. Leia antes de abrir código. Método: `../docs/plans/PLAN-repo-indexing.md` (monorepo).

<!-- index:meta
repo: service-portal-bff
tipo: spring-boot-api (BFF, proxy do frontend)
fonte-de-verdade: build.gradle.kts, src/main/resources/application.yml, src/main/java/com/serviceportal/bff
atualizado-em: 2026-11-07
-->

## Resumo

Único ponto de entrada do frontend. Valida o JWT do Authentik, serve menu e UI schema (Server Driven UI, hardcoded, sem banco), faz proxy de CRUD para o `service-portal-manager` e de execução para o `generic-orchestrator`. O frontend nunca fala direto com o Manager nem com o Orchestrator.

<!-- gen:size -->Tamanho: 33 arquivos .java em `src/main`, 22 em `src/test`.<!-- /gen:size -->

Stack: Java 21, Spring Boot 3.4.5 (não atualizar), Gradle Kotlin DSL, WebClient (nunca RestTemplate), OAuth2 Resource Server com JWKS, springdoc 2.8.14. Porta `SERVER_PORT` (8081).

## Endpoints

| Rota | Auth | Onde |
|---|---|---|
| `GET /bff/health`, `GET /bff/auth/config`, `POST /bff/auth/login`, `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`, `/swagger-ui/**`, `/v3/api-docs/**` | público | `SecurityConfig`; `controller/BffMenuController`, `AuthConfigController`, `LoginController` |
| demais `/actuator/**` (`env`, `loggers`, `threaddump`, `metrics`, `caches`, `beans`) | HTTP Basic, role `ACTUATOR` (nunca Bearer) | `security/ActuatorSecurityConfig` |
| `GET /bff/menu`, `GET /bff/features/{featureId}/ui-schema` | JWT | `controller/BffMenuController` |
| `/bff/flows...`: lista, detalhe, `/yaml`, `/examples`, CRUD, execução v1 e v2 (POST em executions e executions/v2) | JWT + `ADMIN` ou `WORKFLOWS` | `controller/FlowProxyController` |
| `/bff/integrations...` (lista, versões, detalhe, CRUD) | `ADMIN` ou `WORKFLOWS`; escrita só `ADMIN` | `controller/IntegrationProxyController` |
| `/bff/contracts...` | leitura `ADMIN`, `WORKFLOWS`, `RULES`; escrita `ADMIN`, `WORKFLOWS` | `controller/ContractProxyController` |
| `/bff/validations...` | `ADMIN` ou `RULES` | `controller/ValidationProxyController` |

## Fluxo

1. `SecurityConfig.securityFilterChain` libera as rotas públicas e exige JWT no resto. `jwtDecoder` valida assinatura via JWKS, prazo e `iss` contra `bff.auth.allowed-issuers`.
2. O claim `groups` vira authority sem prefixo (`jwtAuthenticationConverter`) e é checado por `@PreAuthorize` nos controllers.
3. O controller chama `client/ManagerClient` (CRUD) ou `client/OrchestratorClient` (execução v1 e v2).
4. Os clients usam o token server-to-server de `ManagerAuthService` ou `OrchestratorAuthService`. O token fica em memória e é renovado quando falta menos de 60 s para expirar.
5. `WebClientResponseException.NotFound` vira 404 no `FlowProxyController`; 409 e 400 também são repassados na criação.

O token do usuário nunca é repassado aos backends. `POST /bff/auth/login` autentica via Authentik Flow Executor em `client/AuthentikLoginService`.

## Mapa

Base: `src/main/java/com/serviceportal/bff/`.

| Caminho | Papel |
|---|---|
| `BffApplication` | Entry point |
| `controller/*` | 7 controllers: menu e UI schema, auth config, login, proxies de flows, integrations, contracts e validations |
| `client/ManagerClient` | CRUD no Manager. Integrations, contracts e validations usam helpers privados `*Resource` com `/manager/{resource}` |
| `client/OrchestratorClient` | Execução em `/api/v1/...` e `/api/v2/...` |
| `client/ManagerAuthService`, `client/OrchestratorAuthService` | Login `POST /api/auth/tokens` e cache do JWT |
| `client/HttpTimeouts`, `client/BackendTimeoutException`, `client/TokenExpiry`, `client/UnauthorizedRetryFilter` | Prazo do `.block()`, validade do token e retry de 401 |
| `controller/BackendExceptionHandler` | `@RestControllerAdvice`: 4xx igual, 5xx 502, timeout 504, conexão 503 |
| `client/AuthentikLoginService` | Login de usuário via Flow Executor do Authentik |
| `security/ActuatorSecurityConfig` | Chain Basic `@Order(1)` só de `/actuator/**`; usuário `ACTUATOR` em memória |
| `security/SecurityDefaultsGuard` | Avisa (WARN) senhas default `admin`; falha em `prod`/`strict` |
| `config/SecurityConfig` | Chain da API `@Order(2)`: rotas públicas, decoder JWT, conversor de `groups`, CORS de `/bff/**` |
| `config/WebClientConfig` | Beans `orchestratorWebClient` e `managerWebClient` |
| `config/AuthProperties`, `BffProperties`, `ManagerProperties`, `HttpProperties` | `bff.auth`, `bff.orchestrator`, `bff.manager`, `bff.http` |
| `config/OpenApiConfig` | Metadados do Swagger |
| `dto/*` | DTOs de menu, UI schema, auth config e login |

Recursos: `src/main/resources/application.yml`, `application-docker.yml`. Testes em `src/test/java/com/serviceportal/bff/{client,config,controller}`.

## Quero mudar... onde

| Mudança | Onde |
|---|---|
| Item de menu | `ALL_ITEMS` em `BffMenuController`, com `requiredGroup(...)` |
| UI schema de uma feature | `switch` em `BffMenuController.uiSchema` (um `case` por `featureId`) |
| Quem acessa um endpoint | `@PreAuthorize` no controller |
| Rota pública da API | `requestMatchers(...)` em `SecurityConfig.securityFilterChain` (`@Order(2)`) |
| Segurança do actuator | `security/ActuatorSecurityConfig` |
| Defaults inseguros | `security/SecurityDefaultsGuard` |
| Issuers aceitos | `bff.auth.allowed-issuers` no `application.yml`; validação em `SecurityConfig.jwtDecoder` |
| CORS | `SecurityConfig.corsConfigurationSource`; origens em `bff.cors.allowed-origins` |
| Nova rota de flow | método em `FlowProxyController` e em `ManagerClient` ou `OrchestratorClient` |
| Novo recurso do Manager | novo controller `*ProxyController` e métodos `*Resource` em `ManagerClient` |
| Parâmetros OAuth2 do frontend | `bff.auth.*` e `AuthConfigController` |
| Login de usuário | `LoginController` e `AuthentikLoginService` (`bff.auth.authentik-base-url`) |
| URL ou credencial dos backends | `bff.manager.*`, `bff.orchestrator.*`; beans em `WebClientConfig` |
| Swagger | `OpenApiConfig`; paths em `springdoc` no `application.yml` |

## Integrações e config

- Manager: `MANAGER_URL` (`localhost:8082`), `MANAGER_USERNAME`, `MANAGER_PASSWORD` (`admin`). Rotas `/manager/...` e `/api/auth/tokens`.
- Orchestrator: `ORCHESTRATOR_URL` (`localhost:8080`), `ORCHESTRATOR_USERNAME`, `ORCHESTRATOR_PASSWORD`.
- Timeouts HTTP: `bff.http.connect-timeout-ms` (`BFF_HTTP_CONNECT_TIMEOUT_MS`, 2000), `bff.manager.timeout-ms` (`MANAGER_TIMEOUT_MS`, 10000) e `bff.orchestrator.timeout-ms` (`ORCHESTRATOR_TIMEOUT_MS`, 30000, execução de fluxo é mais lenta). Aplicados em `WebClientConfig` (Reactor Netty); todo `.block()` dos clients tem prazo (timeout + 2 s) via `HttpTimeouts`.
- Tokens de serviço (`ManagerAuthService`, `OrchestratorAuthService`): validade vem do claim `exp` do JWT (sem verificar assinatura, só dirige o cache), senão de `expiresIn`/`expires_in` do login, senão 3600 s; renova 60 s antes. `invalidate(token)` descarta o cache; `UnauthorizedRetryFilter` (instalado nos clients) repete a chamada uma vez com token novo ao receber 401.
- Authentik: `AUTHENTIK_JWKS_URI` (URL interna, acessada pelo container), `AUTHENTIK_ISSUER_URI` (URL pública, igual ao `iss` do token), `AUTHENTIK_M2M_ISSUER_URI`, `AUTHENTIK_CLIENT_ID`, `AUTHENTIK_BASE_URL`.
- Actuator: públicos `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`. O resto exige HTTP Basic do usuário com role `ACTUATOR`: `ACTUATOR_USERNAME` (default `actuator`) e `ACTUATOR_PASSWORD` (SEM default: vazio = usuário inexistente = 401 sempre). `heapdump` não é exposto; `health` mostra detalhes só `when-authorized`. O SBA lê esses endpoints pelas metadata `spring.boot.admin.client.instance.metadata.user.name`/`.password`, preenchidas com as mesmas variáveis.
- Guard de segredos: `SECURITY_STRICT=true` ou profile `prod` faz o startup falhar se `MANAGER_PASSWORD`, `ORCHESTRATOR_PASSWORD` ou `SBA_PASSWORD` estiverem em `admin`; nos demais perfis só loga WARN com os nomes (nunca os valores). Senhas não ficam no `ENV` do Dockerfile.
- CORS: `BFF_CORS_ALLOWED_ORIGINS` (lista separada por vírgula, default `http://localhost:5173`). Em produção/compose defina a origem real do frontend, senão o browser é bloqueado. Headers aceitos: `Authorization`, `Content-Type`, `Accept`.
- SBA: `SPRING_BOOT_ADMIN_URL`, `SBA_USERNAME`/`SBA_PASSWORD`. Tracing: `OTEL_EXPORTER_OTLP_ENDPOINT`.
- Demais valores: `src/main/resources/application.yml`. Stack completa: `../docker-compose-service-portal.yml`.

## Comandos

```bash
./gradlew bootRun                           # precisa de Manager, Orchestrator e Authentik no ar
./gradlew test                              # unitários e IT
./gradlew jacocoTestCoverageVerification    # gate >= 95% (classes em coverageIncludes no build.gradle.kts)
./gradlew spotlessApply                     # antes de commit
```

## Armadilhas

- Sem atualizar Java 21 nem Spring Boot 3.4.5, sem RestTemplate.
- Segredos nunca em `ENV` do Dockerfile: injete em runtime. Não volte a liberar `/actuator/**` na chain da API nem exponha `heapdump`. O resource server JWT só roda na chain da API: um Bearer do Authentik não abre o actuator.
- Testes que leem `/actuator/prometheus` com MockMvc precisam de `@AutoConfigureObservability` (o `@SpringBootTest` desliga o export de métricas).
- `AUTHENTIK_JWKS_URI` e `AUTHENTIK_ISSUER_URI` diferem quando o Authentik roda em container. Misturar os dois quebra a validação do `iss`.
- Chamadas com `uri(Function)` no `ManagerClient` precisam do atributo `WebClient.class.getName() + ".uriTemplate"`. Sem ele a métrica `http_client_requests_seconds` fica com `uri=none` e cardinalidade alta.
- Rota nova em `/bff/**` exige `@PreAuthorize` explícito: `SecurityConfig` só exige JWT, não grupo.
- Todo `.java` precisa do header de licença (`license-header.txt`, cópia do arquivo na raiz do monorepo: mantenha igual), senão o Spotless falha.
- O gate JaCoCo cobre config, clients, `FlowProxyController` e os controllers de proxy e menu. Mude essas classes com teste.
- Os dois planos de auth são independentes. Não repasse o JWT do usuário ao Manager ou Orchestrator.
- Todo `.block()` em client novo precisa de `.timeout(deadline, HttpTimeouts.onTimeout(deadline))`; sem prazo, um backend travado prende uma thread do Tomcat.
- Erros de backend viram resposta HTTP em `BackendExceptionHandler` (`@RestControllerAdvice`): backend 4xx mantém o status; backend 5xx ou status inesperado vira 502; timeout (Netty, `TimeoutException`, `BackendTimeoutException`) vira 504; conexão recusada ou falha de rede vira 503. Corpo `{status, error, message}` sem o corpo do backend (vazaria detalhes). Os controllers ainda capturam NotFound/Conflict/BadRequest antes do advice; não remova esses `catch` sem revisar os testes de controller.
- O retry de 401 vive só no `UnauthorizedRetryFilter`; client novo precisa instalá-lo (`withTokenRetry`) e `getToken()` bloqueante roda em `boundedElastic`, nunca no event loop do Netty.
