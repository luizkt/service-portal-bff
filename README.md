# Service Portal BFF

Backend for Frontend do Service Portal, baseado em Java 21 LTS + Spring Boot 3.4 LTS + Gradle.

## Visão Geral

Camada intermediária entre o frontend React e os serviços de backend. Aplica o padrão **Server Driven UI**: o BFF descreve dinamicamente quais features aparecem na sidebar e qual schema de UI cada feature usa, deixando o frontend "burro" (sem regras de negócio).

Responsabilidades:

- **Menu e UI Schema**: expõe `/bff/menu` e `/bff/features/{featureId}/ui-schema` para o frontend renderizar a navegação e cada tela
- **Proxy de fluxos**:
  - **CRUD** → repassa para o `service-portal-manager` (porta 8082) — único dono da collection `workflows`
  - **Execução** → repassa para o `generic-orchestrator` (porta 8080) — `POST /bff/flows/{flowId}/versions/{version}/executions`
- **Autenticação tripla**:
  - Inbound (frontend → BFF): valida tokens JWT do Authentik via OAuth2 Resource Server
  - Outbound 1 (BFF → Manager): server-to-server `POST /api/auth/tokens`, token em cache renovado automaticamente
  - Outbound 2 (BFF → Orquestrador): server-to-server `POST /api/auth/tokens`, token em cache renovado automaticamente

---

## Stack

| Componente | Versão |
|---|---|
| Java | 21 LTS |
| Spring Boot | 3.4.5 LTS |
| Gradle | Kotlin DSL |
| Spring WebFlux | WebClient (chamadas ao orquestrador) |
| Spring Security | OAuth2 Resource Server (validação JWT do Authentik) |
| Lombok | — |

---

## Estrutura do Projeto

```
src/main/java/com/serviceportal/bff/
├── BffApplication.java
├── config/
│   ├── BffProperties.java          # @ConfigurationProperties("bff.orchestrator")
│   ├── ManagerProperties.java      # @ConfigurationProperties("bff.manager")
│   ├── AuthProperties.java         # @ConfigurationProperties("bff.auth")
│   ├── WebClientConfig.java        # Beans WebClient (orchestrator + manager)
│   └── SecurityConfig.java         # OAuth2 Resource Server + CORS
├── client/
│   ├── OrchestratorAuthService.java# Login server-to-server (orquestrador)
│   ├── OrchestratorClient.java     # POST /api/orchestrate (única chamada restante)
│   ├── ManagerAuthService.java     # Login server-to-server (Manager)
│   └── ManagerClient.java          # CRUD: list/get/create/update/delete + getYaml
├── controller/
│   ├── BffMenuController.java      # /bff/health, /bff/menu, /bff/features/{id}/ui-schema
│   ├── AuthConfigController.java   # /bff/auth/config (público — OAuth2/PKCE)
│   └── FlowProxyController.java    # CRUD → Manager; orchestrate → Orchestrator
└── dto/
    ├── LoginRequest.java
    ├── LoginResponse.java
    ├── AuthConfigDto.java
    ├── MenuItemDto.java
    └── UiSchemaDto.java
src/main/resources/
└── application.yml
src/test/java/com/serviceportal/bff/
├── client/
│   ├── ManagerAuthServiceTest.java
│   ├── ManagerClientTest.java
│   ├── OrchestratorAuthServiceTest.java
│   └── OrchestratorClientTest.java
├── config/
│   ├── AuthPropertiesTest.java
│   ├── SecurityConfigTest.java
│   └── SecurityConfigIT.java       # @SpringBootTest + MockMvc
└── controller/
    ├── AuthConfigControllerTest.java
    └── FlowProxyControllerTest.java
```

---

## Configuração

### `application.yml` — variáveis principais

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # URL JWKS interna (o BFF chama por DNS de container)
          jwk-set-uri: ${AUTHENTIK_JWKS_URI:http://localhost:9000/application/o/service-portal/jwks/}

server:
  port: ${SERVER_PORT:8081}

bff:
  orchestrator:
    # Apenas execução de fluxos — POST /api/flows/{flowId}/versions/{version}/executions
    base-url: ${ORCHESTRATOR_URL:http://localhost:8080}
    username: ${ORCHESTRATOR_USERNAME:admin}
    password: ${ORCHESTRATOR_PASSWORD:admin}
  manager:
    # CRUD de fluxos — service-portal-manager (porta 8082)
    base-url: ${MANAGER_URL:http://localhost:8082}
    username: ${MANAGER_USERNAME:admin}
    password: ${MANAGER_PASSWORD:admin}
  auth:
    # URL pública — deve bater com o claim "iss" dos tokens emitidos pelo Authentik
    issuer-uri: ${AUTHENTIK_ISSUER_URI:http://localhost:9000/application/o/service-portal/}
    # Client ID público da SPA cadastrado no Authentik (provider OAuth2/OIDC)
    client-id: ${AUTHENTIK_CLIENT_ID:service-portal-spa}
    # Scopes que o frontend solicita no /authorize
    scopes:
      - openid
      - profile
      - email
```

### Variáveis de ambiente

| Variável | Descrição | Default |
|---|---|---|
| `SERVER_PORT` | Porta do BFF | `8081` |
| `ORCHESTRATOR_URL` | Base URL do `generic-orchestrator` (execução) | `http://localhost:8080` |
| `ORCHESTRATOR_USERNAME` | Usuário do orquestrador (auth server-to-server) | `admin` |
| `ORCHESTRATOR_PASSWORD` | Senha do orquestrador | `admin` |
| `MANAGER_URL` | Base URL do `service-portal-manager` (CRUD de fluxos) | `http://localhost:8082` |
| `MANAGER_USERNAME` | Usuário do Manager (auth server-to-server) | `admin` |
| `MANAGER_PASSWORD` | Senha do Manager | `admin` |
| `AUTHENTIK_JWKS_URI` | Endpoint JWKS do Authentik (interno) | `http://localhost:9000/application/o/service-portal/jwks/` |
| `AUTHENTIK_ISSUER_URI` | Issuer público do Authentik (validação do claim `iss`) | `http://localhost:9000/application/o/service-portal/` |
| `AUTHENTIK_CLIENT_ID` | Client ID público da SPA exposto pelo `/bff/auth/config` | `service-portal-spa` |

### Cache de token (orquestrador e Manager)

`OrchestratorAuthService` e `ManagerAuthService` mantêm tokens separados em memória e renovam quando faltam menos de 60s para expirar (assume TTL de 3600s, mesmo padrão dos dois serviços). Cada cliente (`OrchestratorClient`, `ManagerClient`) injeta `Authorization: Bearer <token>` automaticamente — o frontend nunca vê essas credenciais.

---

## Como Executar

```bash
# 1. Subir a infraestrutura (orquestrador + Mongo + Authentik)
docker compose up -d   # no diretório raiz do service-portal

# 2. Build
./gradlew build

# 3. Apenas testes
./gradlew test

# 4. Rodar localmente
./gradlew bootRun
```

O BFF sobe em `http://localhost:8081`.

---

## API

Todos os endpoints (exceto `/bff/health`, `/bff/auth/config` e `/actuator/health|info`) exigem `Authorization: Bearer <token>` emitido pelo Authentik.

### Endpoints

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/bff/health` | Healthcheck (público) |
| GET | `/bff/auth/config` | Configuração OAuth2/PKCE para o SPA (público — issuer, client_id, scopes) |
| GET | `/bff/menu` | Itens da sidebar (Server Driven UI) |
| GET | `/bff/features/{featureId}/ui-schema` | Schema JSON da feature |
| GET | `/bff/flows?page=&size=&sort=` | Lista paginada (proxy ao Manager) — sem `yamlContent` |
| GET | `/bff/flows/{flowId}/versions/{version}` | Metadados de um fluxo (proxy ao Manager) |
| GET | `/bff/flows/{flowId}/versions/{version}/yaml` | YAML cru do fluxo (proxy ao Manager) |
| POST | `/bff/flows` | Cria fluxo no Manager (body: YAML como `text/plain`) |
| PUT | `/bff/flows/{flowId}/versions/{version}` | Atualiza fluxo no Manager (body: YAML) |
| DELETE | `/bff/flows/{flowId}/versions/{version}` | Soft-delete no Manager (`active=false`) |
| POST | `/bff/flows/{flowId}/versions/{version}/executions` | Executa fluxo (proxy ao orquestrador, body: JSON payload) |
| GET | `/actuator/health` | Health check (público) |

### Server Driven UI

#### `GET /bff/menu`

```json
[
  {
    "id": "flow-manager",
    "label": "Gerenciador de Fluxos",
    "icon": "workflow",
    "uiSchemaUrl": "/bff/features/flow-manager/ui-schema"
  }
]
```

| Campo | Descrição |
|---|---|
| `id` | Identificador único da feature |
| `label` | Texto exibido no menu |
| `icon` | Nome do ícone (mapeado pelo frontend) |
| `uiSchemaUrl` | Endpoint `GET /bff/features/{featureId}/ui-schema` que devolve o schema da tela |

#### `GET /bff/features/{featureId}/ui-schema`

```json
{
  "featureId": "flow-manager",
  "type": "flow-manager",
  "title": "Gerenciador de Fluxos"
}
```

| Campo | Descrição |
|---|---|
| `featureId` | Mesmo `id` retornado em `/bff/menu` |
| `type` | Discriminador usado pelo `ComponentRenderer` do frontend |
| `title` | Título exibido na área principal |

> Para adicionar uma nova feature: incluir um item em `BffMenuController.menu()`, um case em `uiSchema(...)` e o componente correspondente no `ComponentRenderer` do frontend.

#### `GET /bff/auth/config`

Endpoint **público** consumido pela SPA na inicialização — descreve o IdP sem hardcoded no bundle.

```json
{
  "issuerUri": "http://localhost:9000/application/o/service-portal/",
  "clientId": "service-portal-spa",
  "scopes": ["openid", "profile", "email"]
}
```

A SPA usa estes valores para montar a URL de `/authorize` (PKCE/S256), redirecionar o usuário ao Authentik e trocar o `code` recebido no callback por um access token.

### Proxy de fluxos

```bash
# Listar
curl http://localhost:8081/bff/flows \
  -H "Authorization: Bearer <token-authentik>"

# Criar (YAML como text/plain)
curl -X POST http://localhost:8081/bff/flows \
  -H "Authorization: Bearer <token-authentik>" \
  -H "Content-Type: text/plain" \
  --data-binary @meu-fluxo.yml

# Executar (version + flowId no path)
curl -X POST http://localhost:8081/bff/orchestrate/v1/meu-fluxo \
  -H "Authorization: Bearer <token-authentik>" \
  -H "Content-Type: application/json" \
  -d '{"campo":"valor"}'
```

**Atenção:** o endpoint de execução do orquestrador exige o segmento `version` no path. O BFF apenas repassa, então o frontend precisa enviá-lo (o `FlowManager` usa `v1` por padrão).

---

## Segurança

### Inbound — frontend → BFF

`SecurityConfig` configura o BFF como OAuth2 Resource Server:

- Decoder `NimbusJwtDecoder` busca a chave pública via `AUTHENTIK_JWKS_URI`
- Valida assinatura + claim `iss` contra `AUTHENTIK_ISSUER_URI`
- `STATELESS` — sem sessão, sem CSRF
- CORS liberado em `/bff/**` para qualquer origem (ajustar em produção)
- Endpoints públicos: `/bff/health`, `/bff/auth/config`, `/actuator/health`, `/actuator/info`

### Fluxo OAuth2/PKCE (SPA pública)

```
1. SPA → GET /bff/auth/config             → issuer, client_id, scopes
2. SPA → window.location = ${issuer}authorize/?…&code_challenge=…&state=…
3. Authentik → redirect /auth/callback?code=…&state=…
4. SPA → POST ${issuer}token/             (code + code_verifier + client_id)
5. SPA → guarda access_token em sessionStorage
6. SPA → BFF: Authorization: Bearer <access_token>
7. BFF valida JWT (assinatura JWKS + iss) — só então responde
```

Logout: SPA chama `${issuer}end-session/?id_token_hint=…&post_logout_redirect_uri=…`. O BFF não mantém sessão — basta apagar o token do `sessionStorage`. Em respostas 401 do BFF, o frontend invalida a sessão local automaticamente.

### Outbound 1 — BFF → service-portal-manager (CRUD de fluxos)

`ManagerAuthService` cuida do login no Manager e mantém o JWT em cache. `ManagerClient` injeta `Authorization: Bearer <token>` em todas as chamadas — POST/GET/PUT/DELETE em `/manager/flows[...]` e o GET YAML cru em `/manager/flows/{flowId}/versions/{version}/yaml`.

### Outbound 2 — BFF → orquestrador (execução de fluxos)

`OrchestratorAuthService` cuida do login no orquestrador. `OrchestratorClient` mantém apenas a chamada de execução (`POST /api/flows/{flowId}/versions/{version}/executions`); todo CRUD migrou para o Manager.

---

## Testes e cobertura

```bash
./gradlew test jacocoTestReport jacocoTestCoverageVerification
# Relatório HTML: build/reports/jacoco/test/html/index.html
```

O gate `jacocoTestCoverageVerification` exige **≥ 95% de cobertura de instruções** nas classes da feature de auth (`SecurityConfig`, `AuthProperties`, `AuthConfigController`, `AuthConfigDto`). Cobertura atual: **100%** (10 testes).

---

## Docker

```bash
docker build -t service-portal-bff .
docker run --rm -p 8081:8081 \
  -e ORCHESTRATOR_URL=http://orquestrador:8080 \
  -e AUTHENTIK_JWKS_URI=http://authentik-server:9000/application/o/service-portal/jwks/ \
  -e AUTHENTIK_ISSUER_URI=http://localhost:9000/application/o/service-portal/ \
  -e AUTHENTIK_CLIENT_ID=service-portal-spa \
  service-portal-bff
```

O `Dockerfile` faz build em duas etapas (Gradle + Temurin JRE 21) e expõe a porta `8081`. Variáveis de ambiente já têm defaults sensatos para desenvolvimento local.
