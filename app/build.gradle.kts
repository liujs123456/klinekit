plugins {
    application
    id("com.gradleup.shadow") version "9.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.picocli)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.klinekit.cli.Main"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("klinekit")
    archiveClassifier.set("")
    archiveVersion.set("0.1.0")
    manifest {
        attributes["Main-Class"] = "com.klinekit.cli.Main"
    }
}
