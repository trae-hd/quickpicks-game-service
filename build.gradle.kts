plugins {
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.spring") version "2.1.10"
    kotlin("plugin.jpa") version "2.1.10"
    // NOTE: No kotlin("plugin.serialization") — Jackson is the sole serialisation framework
}

group = "io.qplay"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // ─── Spring Boot starters ─────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // ─── Kotlin ───────────────────────────────────────────────────
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // NOTE: No kotlinx-coroutines — RestClient is used for all HTTP calls.
    // Coroutines are incompatible with ThreadLocal-based TenantContext.
    // NOTE: No kotlinx-serialization — Jackson is the sole serialisation framework.

    // ─── Database ─────────────────────────────────────────────────
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // ─── JWT ──────────────────────────────────────────────────────
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // ─── JSONPath (feed mapping engine) ───────────────────────────
    implementation("com.jayway.jsonpath:json-path:2.9.0")

    // ─── Distributed scheduling (prevents duplicate polling across pods) ──
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.16.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-redis-spring:5.16.0")

    // ─── Redis (Lettuce is the Spring default) ────────────────────
    // No extra dependency — spring-boot-starter-data-redis includes Lettuce

    // ─── OpenAPI (contract-first type safety across repo boundary) ──
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // ─── Observability ────────────────────────────────────────────
    implementation("io.micrometer:micrometer-registry-prometheus")

    // ─── Test ─────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
