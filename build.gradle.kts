plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25" // Bu o'zi Entity-larni 'open' qiladi
    id("org.springframework.boot") version "3.4.1" // 3.5.x hali milestone bo'lishi mumkin, 3.4 stable
    id("io.spring.dependency-management") version "1.1.7"
}

group = "uz.zero"
version = "0.0.1-SNAPSHOT"
description = "app-support"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starterlar
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Telegram Bot (Eng barqaror yangi versiya)
    // Eslatma: 9.2.0 versiya mavjud emas, 7.10.0 yoki 8.x ishlatiladi
    implementation("org.telegram:telegrambots-springboot-longpolling-starter:7.10.0")
    implementation("org.telegram:telegrambots-client:7.10.0")

    // Kotlin uchun zaruriy modullar
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Database
    runtimeOnly("org.postgresql:postgresql")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
// allOpen bloki o'chirildi, chunki 'kotlin("plugin.jpa")' buni avtomatik bajaradi.
// Agar maxsus annotatsiyalar bo'lmasa, buni yozish shart emas.

tasks.withType<Test> {
    useJUnitPlatform()
}