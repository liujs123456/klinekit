plugins {
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

tasks.withType<Test>().configureEach {
    val dockerSocket = File("${System.getProperty("user.home")}/.docker/run/docker.sock")
    if (dockerSocket.exists()) {
        environment("DOCKER_HOST", "unix://${dockerSocket.absolutePath}")
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":persistence"))
    implementation(rootProject.libs.spring.boot.starter.web)
    implementation(rootProject.libs.spring.boot.starter.data.jpa)
    implementation(rootProject.libs.flyway.core)
    runtimeOnly(rootProject.libs.flyway.postgres)
    runtimeOnly(rootProject.libs.postgresql)
    runtimeOnly(rootProject.libs.h2)
    implementation(rootProject.libs.springdoc.openapi)

    testImplementation(rootProject.libs.spring.boot.starter.test)
    testImplementation(rootProject.libs.testcontainers.postgresql)
    testImplementation(rootProject.libs.testcontainers.junit)
    testRuntimeOnly(rootProject.libs.h2)
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
