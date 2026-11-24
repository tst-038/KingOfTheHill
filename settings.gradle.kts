plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "KingOfTheHill"

dependencyResolutionManagement {
    repositories {
        // MiniGami: a local build first (publishToMavenLocal in the MiniGami repo), else its releases on GitHub
        // Packages, which needs the GitHubPackagesUsername and GitHubPackagesPassword Gradle properties (a token
        // with read:packages).
        mavenLocal {
            content { includeGroup("io.github.tst-038") }
        }
        maven("https://maven.pkg.github.com/tst-038/MiniGami") {
            name = "GitHubPackages"
            credentials {
                username = providers.gradleProperty("GitHubPackagesUsername").orNull
                password = providers.gradleProperty("GitHubPackagesPassword").orNull
            }
            content { includeGroup("io.github.tst-038") }
        }
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/releases/")
    }
}
