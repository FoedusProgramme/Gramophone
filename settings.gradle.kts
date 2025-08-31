@file:Suppress("UnstableApiUsage")

include(":alacdecoder")


pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.philburk")
            }
        }
    }
}

rootProject.name = "Gramophone"
// Removed media3 includeBuild for MediaPlayer replacement
include(":hificore", ":app", ":baselineprofile")
