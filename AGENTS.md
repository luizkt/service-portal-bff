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

<!-- gen:size -->Tamanho: 28 arquivos .java em `src/main`, 15 em `src/test`.<!-- /gen:size -->

Stack: Java 21, Spring Boot 3.4.5 (não atualizar), Gradle Kotlin DSL, WebClient (nunca RestTemplate), OAuth2 Resource Server com JWKS, springdoc 2.8.14. Porta `SERVER_PORT` (8081).

## Endpoints

| Rota | Auth | Onde |
|---|---|---|
| `GET /bff/health`, `GET /bff/auth/config`, `POST /bff/auth/login`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**` | público | `SecurityConfig`; `controller/BffMenuController`, `AuthConfigController`, `LoginController` |
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
| `client/AuthentikLoginService` | Login de usuário via Flow Executor do Authentik |
| `config/SecurityConfig` | Rotas públicas, decoder JWT, conversor de `groups`, CORS de `/bff/**` |
| `config/WebClientConfig` | Beans `orchestratorWebClient` e `managerWebClient` |
| `config/AuthProperties`, `BffProperties`, `ManagerProperties` | `bff.auth`, `bff.orchestrator`, `bff.manager` |
| `config/OpenApiConfig` | Metadados do Swagger |
| `dto/*` | DTOs de menu, UI schema, auth config e login |

Recursos: `src/main/resources/application.yml`, `application-docker.yml`. Testes em `src/test/java/com/serviceportal/bff/{client,config,controller}`.

## Quero mudar... onde

| Mudança | Onde |
|---|---|
| Item de menu | `ALL_ITEMS` em `BffMenuController`, com `requiredGroup(...)` |
| UI schema de uma feature | `switch` em `BffMenuController.uiSchema` (um `case` por `featureId`) |
| Quem acessa um endpoint | `@PreAuthorize` no controller |
| Rota pública | `requestMatchers(...)` em `SecurityConfig.securityFilterChain` |
| Issuers aceitos | `bff.auth.allowed-issuers` no `application.yml`; validação em `SecurityConfig.jwtDecoder` |
| CORS | `SecurityConfig.corsConfigurationSource` (hoje `*`) |
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
- Authentik: `AUTHENTIK_JWKS_URI` (URL interna, acessada pelo container), `AUTHENTIK_ISSUER_URI` (URL pública, igual ao `iss` do token), `AUTHENTIK_M2M_ISSUER_URI`, `AUTHENTIK_CLIENT_ID`, `AUTHENTIK_BASE_URL`.
- SBA: `SPRING_BOOT_ADMIN_URL`. Tracing: `OTEL_EXPORTER_OTLP_ENDPOINT`.
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
- `AUTHENTIK_JWKS_URI` e `AUTHENTIK_ISSUER_URI` diferem quando o Authentik roda em container. Misturar os dois quebra a validação do `iss`.
- Chamadas com `uri(Function)` no `ManagerClient` precisam do atributo `WebClient.class.getName() + ".uriTemplate"`. Sem ele a métrica `http_client_requests_seconds` fica com `uri=none` e cardinalidade alta.
- Rota nova em `/bff/**` exige `@PreAuthorize` explícito: `SecurityConfig` só exige JWT, não grupo.
- Todo `.java` precisa do header de licença (`license-header.txt`, cópia do arquivo na raiz do monorepo: mantenha igual), senão o Spotless falha.
- O gate JaCoCo cobre config, clients, `FlowProxyController` e os controllers de proxy e menu. Mude essas classes com teste.
- Os dois planos de auth são independentes. Não repasse o JWT do usuário ao Manager ou Orchestrator.
- Todo `.block()` em client novo precisa de `.timeout(deadline, HttpTimeouts.onTimeout(deadline))`; sem prazo, um backend travado prende uma thread do Tomcat.
