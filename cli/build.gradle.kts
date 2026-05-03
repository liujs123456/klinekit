plugins {
    application
    id("com.gradleup.shadow") version "9.0.0"
}

dependencies {
    implementation(project(":core"))
    implementation(rootProject.libs.picocli)

    testImplementation(rootProject.libs.junit.jupiter)
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
}

application {
    mainClass = "com.klinekit.cli.Main"
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("klinekit-cli")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    manifest {
        attributes["Main-Class"] = "com.klinekit.cli.Main"
    }
}
