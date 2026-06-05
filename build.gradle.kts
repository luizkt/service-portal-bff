import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
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

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
    "com/serviceportal/bff/controller/IntegrationProxyController.class",
    "com/serviceportal/bff/controller/ContractProxyController.class",
    "com/serviceportal/bff/controller/ValidationProxyController.class",
    "com/serviceportal/bff/controller/BffMenuController.class"
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
