# syntax=docker/dockerfile:1
# ─── Build ────────────────────────────────────────────────────────────────────
FROM gradle:8.10-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    gradle bootJar --no-daemon -x test

# ─── Runtime ──────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/service-portal-bff.jar app.jar

# ── Orquestrador ──────────────────────────────────────────────────────────────
ENV ORCHESTRATOR_URL=http://localhost:8080 \
    ORCHESTRATOR_USERNAME=admin \
    ORCHESTRATOR_PASSWORD=admin

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

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
