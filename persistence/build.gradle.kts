dependencies {
    implementation(project(":core"))
    implementation(rootProject.libs.spring.boot.starter.data.jpa)
    implementation(rootProject.libs.flyway.core)
    runtimeOnly(rootProject.libs.flyway.postgres)
    runtimeOnly(rootProject.libs.postgresql)

    testImplementation(rootProject.libs.spring.boot.starter.test)
    testImplementation(rootProject.libs.testcontainers.postgresql)
    testImplementation(rootProject.libs.testcontainers.junit)
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
