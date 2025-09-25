import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("java")
    id("com.diffplug.spotless") version "7.2.1"
    id("jacoco")
}

group = "org.ddamme"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {

    developmentOnly("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("io.micrometer:micrometer-registry-otlp")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(platform("software.amazon.awssdk:bom:2.33.13"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:sts")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.2")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:localstack")
}

jacoco {
    toolVersion = "0.8.12"
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.2")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        // Show which tests ran and which failed in console
        events(
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        // Flip to true if you want System.out/err from tests
        showStandardStreams = false
    }
    // Print a concise summary at the end of the test run
    addTestListener(object : org.gradle.api.tasks.testing.TestListener {
        override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) {}
        override fun beforeTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor) {}
        override fun afterTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {}
        override fun afterSuite(suite: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
            if (suite.parent == null) {
                println("Results: ${result.resultType} (${result.testCount} tests, ${result.successfulTestCount} successes, ${result.failedTestCount} failures, ${result.skippedTestCount} skipped)")
            }
        }
    })
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

// Disable plain JAR generation to simplify Docker build
tasks.named<Jar>("jar") {
    enabled = false
}

// Set deterministic boot JAR name
tasks.bootJar {
    archiveFileName.set("app.jar")
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets["main"].output
        compileClasspath += sourceSets["test"].output
        compileClasspath += configurations["testRuntimeClasspath"]
        runtimeClasspath += output
        runtimeClasspath += compileClasspath
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter("test")
    // Always run integration tests, regardless of up-to-date checks
    outputs.upToDateWhen { false }

    // Enable JaCoCo for integration tests
    extensions.configure(JacocoTaskExtension::class) {
        isEnabled = true
        destinationFile = layout.buildDirectory.file("jacoco/integrationTest.exec").get().asFile
    }
}

tasks.check { dependsOn("integrationTest") }

// Standard unit-test report
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Merged report for unit + IT (run on demand)
tasks.register<JacocoReport>("jacocoMergedReport") {
    dependsOn(tasks.test, tasks.named("integrationTest"), tasks.jacocoTestReport)
    executionData(fileTree(layout.buildDirectory) {
        include("**/jacoco/*.exec", "**/jacoco/*.ec")
    })
    sourceSets(sourceSets["main"])
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

spotless {
    java {
        target("src/**/*.java")
        importOrder()
        removeUnusedImports()
        googleJavaFormat("1.22.0")
    }
    // Keep gradle scripts neat
    format("gradle") {
        target("**/*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
