plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "klinekit"

include("core", "cli", "persistence", "api")
