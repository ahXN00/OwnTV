pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // tv.own.owntv:core and :player-core, built from https://github.com/ahXN00/OwnTV_Core.
        // That repository is public, but GitHub's Maven registry demands credentials even for a
        // public package — so resolution needs a token with read:packages. Put it in
        // ~/.gradle/gradle.properties as gpr.user / gpr.token, NEVER in this repo. CI passes the
        // same values through the GITHUB_ACTOR / GPR_TOKEN environment variables, except on a fork
        // pull request, where GitHub withholds secrets and CI builds core from source instead.
        maven {
            name = "OwnTVCore"
            url = uri("https://maven.pkg.github.com/ahXN00/OwnTV_Core")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GPR_TOKEN")).orNull
            }
            content { includeGroup("tv.own.owntv") }
        }
    }
}

// Local development: build against core's own source instead of the published artifact, so a core
// edit reaches this app with no publish step. Gradle substitutes the dependency automatically
// because OwnTV_Core publishes under the same group and artifact ids this app asks for. CI leaves
// owntv.corePath unset and resolves the pinned version instead.
// Set it in ~/.gradle/gradle.properties, never here:  owntv.corePath=E:/MEGA/CODE/AI/OwnTV_Core
providers.gradleProperty("owntv.corePath").orNull?.takeIf { it.isNotBlank() }?.let { includeBuild(it) }

rootProject.name = "OwnTV"
include(":app")
// Baseline-profile generator (audit ST1). Test-only module: it ships nothing to users, it records
// the cold-start journey on a device and writes the profile :app packages.
include(":baselineprofile")
 