dependencies {
    implementation(rootProject.libs.jackson.databind)

    testImplementation(rootProject.libs.junit.jupiter)
    testImplementation(rootProject.libs.assertj.core)
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}
