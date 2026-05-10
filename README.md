# Service Portal BFF

Backend for Frontend do Service Portal, baseado em Java 21 LTS + Spring Boot 3.4 LTS + Gradle.

## Visão Geral

Camada intermediária entre o frontend React e o `generic-orchestrator`. Aplica o padrão **Server Driven UI**: o BFF descreve dinamicamente quais features aparecem na sidebar e qual schema de UI cada feature usa, deixando o frontend "burro" (sem regras de negócio).

Responsabilidades:

- **Menu e UI Schema**: expõe `/bff/menu` e `/bff/ui/{featureId}` para o frontend renderizar a navegação e cada tela
- **Proxy de fluxos**: encapsula o CRUD de fluxos do orquestrador e a execução (`/bff/flows`, `/bff/orchestrate/{version}/{flowId}`)
- **Autenticação dual**:
  - Inbound (frontend → BFF): valida tokens JWT emitidos pelo Authentik via OAuth2 Resource Server
  - Outbound (BFF → orquestrador): autentica server-to-server em `POST /api/auth/login` do orquestrador, mantendo o token em cache e renovando automaticamente

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
│   ├── WebClientConfig.java        # Bean WebClient do orquestrador
│   └── SecurityConfig.java         # OAuth2 Resource Server + CORS
├── client/
│   ├── OrchestratorAuthService.java# Login server-to-server + cache de token
│   └── OrchestratorClient.java     # Chamadas WebClient ao orquestrador
├── controller/
│   ├── BffMenuController.java      # /bff/health, /bff/menu, /bff/ui/{id}
│   └── FlowProxyController.java    # /bff/flows[...], /bff/orchestrate/{ver}/{id}
└── dto/
    ├── LoginRequest.java
    ├── LoginResponse.java
    ├── MenuItemDto.java
    └── UiSchemaDto.java
src/main/resources/
└── application.yml
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
    base-url: ${ORCHESTRATOR_URL:http://localhost:8080}
    username: ${ORCHESTRATOR_USERNAME:admin}
    password: ${ORCHESTRATOR_PASSWORD:admin}
  auth:
    # URL pública — deve bater com o claim "iss" dos tokens emitidos pelo Authentik
    issuer-uri: ${AUTHENTIK_ISSUER_URI:http://localhost:9000/application/o/service-portal/}
```

### Variáveis de ambiente

| Variável | Descrição | Default |
|---|---|---|
| `SERVER_PORT` | Porta do BFF | `8081` |
| `ORCHESTRATOR_URL` | Base URL do `generic-orchestrator` | `http://localhost:8080` |
| `ORCHESTRATOR_USERNAME` | Usuário do orquestrador (auth server-to-server) | `admin` |
| `ORCHESTRATOR_PASSWORD` | Senha do orquestrador | `admin` |
| `AUTHENTIK_JWKS_URI` | Endpoint JWKS do Authentik (interno) | `http://localhost:9000/application/o/service-portal/jwks/` |
| `AUTHENTIK_ISSUER_URI` | Issuer público do Authentik (validação do claim `iss`) | `http://localhost:9000/application/o/service-portal/` |

### Cache de token do orquestrador

`OrchestratorAuthService` mantém o token em memória e renova quando faltam menos de 60s para expirar (assume TTL de 3600s). Cada chamada do `OrchestratorClient` injeta `Authorization: Bearer <token>` automaticamente — o frontend nunca vê essa credencial.

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

Todos os endpoints (exceto `/bff/health` e `/actuator/health|info`) exigem `Authorization: Bearer <token>` emitido pelo Authentik.

### Endpoints

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/bff/health` | Healthcheck (público) |
| GET | `/bff/menu` | Itens da sidebar (Server Driven UI) |
| GET | `/bff/ui/{featureId}` | Schema JSON da feature |
| GET | `/bff/flows` | Lista fluxos ativos (proxy) |
| GET | `/bff/flows/{flowId}` | Detalhe de um fluxo |
| POST | `/bff/flows` | Cria fluxo (body: YAML como `text/plain`) |
| PUT | `/bff/flows/{flowId}` | Atualiza fluxo (body: YAML) |
| DELETE | `/bff/flows/{flowId}` | Desativa fluxo |
| POST | `/bff/orchestrate/{version}/{flowId}` | Executa fluxo (body: JSON payload) |
| GET | `/actuator/health` | Health check (público) |

### Server Driven UI

#### `GET /bff/menu`

```json
[
  {
    "id": "flow-manager",
    "label": "Gerenciador de Fluxos",
    "icon": "workflow",
    "uiSchemaUrl": "/bff/ui/flow-manager"
  }
]
```

| Campo | Descrição |
|---|---|
| `id` | Identificador único da feature |
| `label` | Texto exibido no menu |
| `icon` | Nome do ícone (mapeado pelo frontend) |
| `uiSchemaUrl` | Endpoint `GET /bff/ui/{featureId}` que devolve o schema da tela |

#### `GET /bff/ui/{featureId}`

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
- Endpoints públicos: `/bff/health`, `/actuator/health`, `/actuator/info`

### Outbound — BFF → orquestrador

`OrchestratorAuthService` faz o login server-to-server e injeta o `Bearer` em cada request via `OrchestratorClient`. O usuário final do portal nunca vê esse token.

---

## Docker

```bash
docker build -t service-portal-bff .
docker run --rm -p 8081:8081 \
  -e ORCHESTRATOR_URL=http://orquestrador:8080 \
  -e AUTHENTIK_JWKS_URI=http://authentik-server:9000/application/o/service-portal/jwks/ \
  -e AUTHENTIK_ISSUER_URI=http://localhost:9000/application/o/service-portal/ \
  service-portal-bff
```

O `Dockerfile` faz build em duas etapas (Gradle + Temurin JRE 21) e expõe a porta `8081`. Variáveis de ambiente já têm defaults sensatos para desenvolvimento local.
