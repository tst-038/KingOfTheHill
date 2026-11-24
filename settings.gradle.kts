plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "MiniGamiKoth"

dependencyResolutionManagement {
    repositories {
        // MiniGami's api, published with ./gradlew publishToMavenLocal in the MiniGami repo.
        mavenLocal {
            content { includeGroup("io.github.tst-038") }
        }
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/releases/")
    }
}
