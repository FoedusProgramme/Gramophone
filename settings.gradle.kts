@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

gradle.extra.apply {
    set("androidxMediaEnableMidiModule", true)
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
(gradle as ExtensionAware).extra["androidxMediaModulePrefix"] = "media3-"
apply(from = file("media3/core_settings.gradle"))
include(":hificore", ":app", ":baselineprofile")
