# syntax=docker/dockerfile:1
# ─── Build ────────────────────────────────────────────────────────────────────
FROM gradle:8.14-jdk21-alpine AS build
WORKDIR /app
# Camada de dependências: só muda quando os arquivos de build mudam
COPY gradlew settings.gradle.kts build.gradle.kts license-header.txt ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle \
    gradle dependencies --no-daemon > /dev/null
# Código-fonte: mudanças aqui não invalidam a camada acima
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    gradle bootJar --no-daemon -x test

# ─── Runtime ──────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl \
    && addgroup -S app && adduser -S -G app app \
    && chown app:app /app

COPY --from=build --chown=app:app /app/build/libs/service-portal-bff.jar app.jar

# ── Orquestrador (apenas execução de fluxos) ──────────────────────────────────
ENV ORCHESTRATOR_URL=http://localhost:8080 \
    ORCHESTRATOR_USERNAME=admin \
    ORCHESTRATOR_PASSWORD=admin

# ── Service Portal Manager (CRUD de fluxos) ───────────────────────────────────
ENV MANAGER_URL=http://localhost:8082 \
    MANAGER_USERNAME=admin \
    MANAGER_PASSWORD=admin

# ── Authentik (OAuth2 Resource Server) ────────────────────────────────────────
# JWKS_URI: URL de onde o BFF busca as chaves públicas do Authentik.
#   Em Docker: use o hostname interno do container (ex: http://authentik-server:9000/...)
# ISSUER_URI: deve corresponder ao campo "iss" dos tokens emitidos pelo Authentik.
#   Geralmente é a URL pública (ex: http://localhost:9000/...)
ENV AUTHENTIK_JWKS_URI=http://localhost:9000/application/o/service-portal/jwks/ \
    AUTHENTIK_ISSUER_URI=http://localhost:9000/application/o/service-portal/

# ── Servidor ───────────────────────────────────────────────────────────────────
ENV SERVER_PORT=8081 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8081

# Liveness não depende de Manager/Orchestrator/Authentik (o /actuator/health geral devolve 503 sem eles)
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:${SERVER_PORT:-8081}/actuator/health/liveness || exit 1

USER app

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
