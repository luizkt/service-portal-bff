import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.9"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.11.0"
}

group = "com.serviceportal"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly { extendsFrom(configurations.annotationProcessor.get()) }
}

repositories { mavenCentral() }

extra["sbaVersion"] = "4.0.4"

dependencies {
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("de.codecentric:spring-boot-admin-starter-client:${property("sbaVersion")}")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-micrometer-metrics-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotless {
    java {
        licenseHeaderFile(file("${projectDir}/license-header.txt"))
        googleJavaFormat("1.19.2").aosp()
        targetExclude("**/build/**", "**/generated/**")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("service-portal-bff.jar")
}

// JaCoCo: cobertura ≥ 95% nas classes da feature de auth e do refactor para o
// service-portal-manager (CRUD via Manager + execução via Orchestrator).
val coverageIncludes = listOf(
    // Auth do BFF (Authentik + endpoint /bff/auth/config)
    "com/serviceportal/bff/config/SecurityConfig.class",
    "com/serviceportal/bff/config/AuthProperties.class",
    "com/serviceportal/bff/controller/AuthConfigController.class",
    "com/serviceportal/bff/dto/AuthConfigDto.class",
    // Refactor: Manager client + proxy de fluxos
    "com/serviceportal/bff/client/ManagerClient.class",
    "com/serviceportal/bff/client/ManagerAuthService.class",
    "com/serviceportal/bff/client/OrchestratorClient.class",
    "com/serviceportal/bff/client/OrchestratorAuthService.class",
    "com/serviceportal/bff/controller/FlowProxyController.class",
    "com/serviceportal/bff/config/ManagerProperties.class",
    "com/serviceportal/bff/config/BffProperties.class",
    // S4 — telas de gerenciamento: proxies de integrations/contracts/validations + menu
    "com/serviceportal/bff/controller/BackendExceptionHandler.class",
    "com/serviceportal/bff/client/HttpTimeouts.class",
    "com/serviceportal/bff/client/TokenExpiry.class",
    "com/serviceportal/bff/client/UnauthorizedRetryFilter.class",
    "com/serviceportal/bff/client/BackendTimeoutException.class",
    "com/serviceportal/bff/config/HttpProperties.class",
    "com/serviceportal/bff/config/WebClientConfig.class",
    "com/serviceportal/bff/controller/IntegrationProxyController.class",
    "com/serviceportal/bff/controller/ContractProxyController.class",
    "com/serviceportal/bff/controller/ValidationProxyController.class",
    "com/serviceportal/bff/controller/BffMenuController.class",
    // Login via flow executor do Authentik
    "com/serviceportal/bff/client/AuthentikLoginService.class",
    "com/serviceportal/bff/controller/LoginController.class"
)

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { include(coverageIncludes) }
        })
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { include(coverageIncludes) }
        })
    )
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.95".toBigDecimal()
            }
        }
    }
}

// O Boot 4 gerencia o OkHttp 5, mas o MockWebServer dos testes é o 4.12 e usa okhttp3.internal.
// Só os classpaths de teste voltam ao OkHttp 4; a aplicação continua com a versão gerenciada.
configurations
    .matching { it.name == "testCompileClasspath" || it.name == "testRuntimeClasspath" }
    .configureEach {
        resolutionStrategy.force("com.squareup.okhttp3:okhttp:4.12.0")
    }
