# AGENTS.md — service-portal-bff

Guia de contexto para agentes de IA trabalhando neste componente.

---

## Stack

| Item | Versão |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 3.4.5 (LTS) |
| Build | Gradle Kotlin DSL |
| HTTP client | Spring WebFlux WebClient (Netty) |
| Segurança | OAuth2 Resource Server (Authentik via JWKS) |
| Porta | 8081 |

---

## Responsabilidade

O BFF é o **único ponto de entrada do frontend**. Ele:

- Serve o menu e o UI schema (Server Driven UI) — hardcoded, sem banco
- Faz proxy de CRUD de workflows para o `service-portal-manager`
- Faz proxy de execução de fluxos para o `generic-orchestrator`
- Valida o Bearer token Authentik em todos os endpoints protegidos
- Expõe `GET /bff/auth/config` para o frontend obter os parâmetros do OAuth2/PKCE sem hardcoded

O frontend **nunca** fala diretamente com o orquestrador ou o manager.

---

## Endpoints

### Públicos (sem token)

| Método | Path | Descrição |
|---|---|---|
| `GET` | `/bff/health` | Health check simples |
| `GET` | `/bff/auth/config` | Configuração OAuth2 para o frontend (issuer, client_id, scopes) |
| `GET` | `/actuator/health` | Health check Spring Actuator |
| `GET` | `/actuator/info` | Info da aplicação |

### Protegidos (Bearer token Authentik obrigatório)

| Método | Path | Destino | Descrição |
|---|---|---|---|
| `GET` | `/bff/menu` | BFF (hardcoded) | Menu da sidebar |
| `GET` | `/bff/features/{featureId}/ui-schema` | BFF (hardcoded) | Schema de UI por feature |
| `GET` | `/bff/flows` | Manager | Lista fluxos (paginado; `?page=&size=&sort=&status=`) |
| `GET` | `/bff/flows/{flowId}/versions/{version}` | Manager | Metadados de um fluxo |
| `GET` | `/bff/flows/{flowId}/versions/{version}/yaml` | Manager | YAML cru (`application/x-yaml`) |
| `POST` | `/bff/flows` | Manager | Cria fluxo (body: YAML como texto) |
| `PUT` | `/bff/flows/{flowId}/versions/{version}` | Manager | Atualiza fluxo |
| `DELETE` | `/bff/flows/{flowId}/versions/{version}` | Manager | Soft-delete do fluxo |
| `POST` | `/bff/flows/{flowId}/versions/{version}/executions` | Orquestrador | Executa um fluxo |

---

## Segurança — dois planos de auth

**Usuário final → BFF:** OAuth2/PKCE com Authentik. O BFF valida o Bearer token via JWKS (`AUTHENTIK_JWKS_URI`) e verifica o claim `iss` contra `AUTHENTIK_ISSUER_URI`. Configurado como OAuth2 Resource Server.

**BFF → Manager / Orquestrador:** server-to-server com JWT HS512. `ManagerAuthService` e `OrchestratorAuthService` fazem login em `POST /api/auth/tokens` de cada serviço e guardam o JWT em memória. Token é renovado automaticamente em expiração ou 401.

Os dois planos são independentes — o token do usuário final não é repassado ao manager/orquestrador.

---

## Como rodar localmente

### Pré-requisitos

- Java 21 instalado
- Docker + Docker Compose
- Authentik rodando (ou `AUTHENTIK_JWKS_URI` apontando para um mock)

### Opção 1 — App no host, serviços via Docker

```bash
# Na raiz do repositório
docker compose -f docker-compose-service-portal.yml up -d manager orchestrator redis rabbitmq kafka mongodb wiremock

cd service-portal-bff
./gradlew bootRun
```

### Opção 2 — Stack completa containerizada

```bash
# Na raiz do repositório
docker compose -f docker-compose-service-portal.yml up -d
```

---

## Como testar

```bash
cd service-portal-bff

# Testes unitários
./gradlew test

# Testes + relatório de cobertura
./gradlew test jacocoTestReport
# Relatório em: build/reports/jacoco/jacocoTestReport/html/index.html

# Verificação de cobertura (gate ≥ 95% INSTRUCTION nas classes da feature)
./gradlew jacocoTestCoverageVerification

# Build sem testes
./gradlew bootJar -x test

# Build da imagem Docker
docker build -t service-portal-bff:local .
```

### Gate de cobertura JaCoCo

O gate cobre:
- `config/SecurityConfig`, `AuthProperties`, `BffProperties`, `ManagerProperties`
- `controller/AuthConfigController`, `FlowProxyController`
- `client/ManagerClient`, `ManagerAuthService`, `OrchestratorClient`, `OrchestratorAuthService`
- `dto/AuthConfigDto`

Cobertura atual: **100% INSTRUCTION** (661/661).

---

## Estrutura de pacotes relevante

```
src/main/java/com/serviceportal/bff/
├── controller/
│   ├── BffMenuController              # GET /bff/menu, /bff/features/{id}/ui-schema, /bff/health
│   ├── FlowProxyController            # CRUD → Manager; execução → Orquestrador
│   └── AuthConfigController           # GET /bff/auth/config (público)
├── client/
│   ├── ManagerClient                  # WebClient para o service-portal-manager (CRUD)
│   ├── ManagerAuthService             # login server-to-server no Manager, JWT em memória
│   ├── OrchestratorClient             # WebClient para o orquestrador (só execução)
│   └── OrchestratorAuthService        # login server-to-server no Orquestrador, JWT em memória
├── config/
│   ├── SecurityConfig                 # OAuth2 Resource Server (Authentik JWKS) + CORS
│   ├── WebClientConfig                # dois beans: orchestratorWebClient e managerWebClient
│   ├── AuthProperties                 # @ConfigurationProperties("bff.auth")
│   ├── BffProperties                  # @ConfigurationProperties("bff.orchestrator")
│   └── ManagerProperties              # @ConfigurationProperties("bff.manager")
└── dto/
    ├── AuthConfigDto                  # {issuerUri, clientId, scopes}
    ├── MenuItemDto                    # {id, label, icon, uiSchemaUrl}
    └── UiSchemaDto                    # {featureId, type, title}
```

---

## Decisões de design

**Proxy sem lógica de negócio.** O BFF repassa requisições ao Manager e ao Orquestrador sem transformar dados. A única lógica é mapear 404 do backend para 404 do BFF (em vez de 500).

**Dois WebClients separados.** `WebClientConfig` cria `orchestratorWebClient` e `managerWebClient` como beans distintos, qualificados com `@Qualifier`. Timeouts e base URLs são independentes.

**Server Driven UI hardcoded.** Menu e UI schema estão hardcoded no `BffMenuController`. Não há banco ou configuração externa. Para adicionar uma nova feature: adicionar um `case` no `switch` de `uiSchema` e um item na lista de `menu`.

**Auth config público.** `GET /bff/auth/config` é explicitamente liberado em `SecurityConfig` para que o frontend possa configurar o fluxo OAuth2/PKCE sem ter o issuer/client_id hardcoded na imagem.

**CORS aberto em dev.** `allowedOriginPatterns("*")` — restringir em produção conforme necessário.

---

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `SERVER_PORT` | `8081` | Porta do servidor |
| `ORCHESTRATOR_URL` | `http://localhost:8080` | Base URL do generic-orchestrator |
| `ORCHESTRATOR_USERNAME` | `admin` | Credencial server-to-server para o Orquestrador |
| `ORCHESTRATOR_PASSWORD` | `admin` | Credencial server-to-server para o Orquestrador |
| `MANAGER_URL` | `http://localhost:8082` | Base URL do service-portal-manager |
| `MANAGER_USERNAME` | `admin` | Credencial server-to-server para o Manager |
| `MANAGER_PASSWORD` | `admin` | Credencial server-to-server para o Manager |
| `AUTHENTIK_JWKS_URI` | `http://localhost:9000/.../jwks/` | URL interna do JWKS do Authentik (Docker: hostname do container) |
| `AUTHENTIK_ISSUER_URI` | `http://localhost:9000/.../` | URL pública do issuer — deve bater com o claim `iss` dos tokens |
| `AUTHENTIK_CLIENT_ID` | `service-portal-spa` | Client ID público da SPA no Authentik |

**Atenção JWKS vs Issuer:** `AUTHENTIK_JWKS_URI` é acessada pelo container BFF (DNS interno do Docker). `AUTHENTIK_ISSUER_URI` é comparada contra o campo `iss` do token, que é gerado com a URL pública do Authentik. Os dois valores podem ser diferentes quando o Authentik roda em container.

---

## Restrições

- Java 21 LTS, Spring Boot 3.4.5 LTS — não atualizar versões
- Gradle com Kotlin DSL
- Sem trocar WebClient por RestTemplate ou similares
- Frontend fala **somente** com o BFF — nunca diretamente com o Orquestrador ou Manager
