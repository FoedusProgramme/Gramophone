@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.tasks.PackageAndroidArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.util.removeSuffixIfPresent
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.builtin.kotlin)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.resourceplaceholders)
}

android {
    val releaseType = if (project.hasProperty("releaseType")) project.properties["releaseType"].toString()
        else readProperties(file("../package.properties")).getProperty("releaseType")
    val myVersionName = "." + "git rev-parse --short=7 HEAD".runCommand(workingDir = rootDir)
    if (releaseType.contains("\"")) {
        throw IllegalArgumentException("releaseType must not contain \"")
    }

    namespace = "org.akanework.gramophone"
    compileSdk = 36

    signingConfigs {
        create("release") {
            if (project.hasProperty("AKANE_RELEASE_KEY_ALIAS")) {
                storeFile = file(project.properties["AKANE_RELEASE_STORE_FILE"].toString())
                storePassword = project.properties["AKANE_RELEASE_STORE_PASSWORD"].toString()
                keyAlias = project.properties["AKANE_RELEASE_KEY_ALIAS"].toString()
                keyPassword = project.properties["AKANE_RELEASE_KEY_PASSWORD"].toString()
            }
        }
        create("release2") {
            if (project.hasProperty("AKANE2_RELEASE_KEY_ALIAS")) {
                storeFile = file(project.properties["AKANE2_RELEASE_STORE_FILE"].toString())
                storePassword = project.properties["AKANE2_RELEASE_STORE_PASSWORD"].toString()
                keyAlias = project.properties["AKANE2_RELEASE_KEY_ALIAS"].toString()
                keyPassword = project.properties["AKANE2_RELEASE_KEY_PASSWORD"].toString()
            }
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        buildConfig = true
        prefab = true
        compose = true
    }

    packaging {
        dex {
            useLegacyPackaging = false
        }
        jniLibs {
            useLegacyPackaging = false
            // https://issuetracker.google.com/issues/168777344#comment11
            pickFirsts += "lib/arm64-v8a/libdlfunc.so"
            pickFirsts += "lib/armeabi-v7a/libdlfunc.so"
            pickFirsts += "lib/x86/libdlfunc.so"
            pickFirsts += "lib/x86_64/libdlfunc.so"
        }
        resources {
            // https://youtrack.jetbrains.com/issue/KT-48019/Bundle-Kotlin-Tooling-Metadata-into-apk-artifacts
            excludes += "kotlin-tooling-metadata.json"
            // https://issuetracker.google.com/issues/152898926#comment7
            excludes += "META-INF/*.version"
            // https://github.com/Kotlin/kotlinx.coroutines?tab=readme-ov-file#avoiding-including-the-debug-infrastructure-in-the-resulting-apk
            excludes += "DebugProbesKt.bin"
            // covered by AboutLicenses instead
            excludes += "META-INF/androidx/*/*/LICENSE.txt"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        lintConfig = file("lint.xml")
    }

    defaultConfig {
        applicationId = "org.akanework.gramophone"
        // Reasons to not support KK include me.zhanghai.android.fastscroll, WindowInsets for
        // bottom sheet padding, ExoPlayer requiring multidex, vector drawables and poor SD support
        // That said, supporting Android 5.0 costs tolerable amounts of tech debt, and we plan to
        // keep support for it for a while.
        minSdk = 21
        targetSdk = 35
        versionCode = 20
        versionName = "1.0.17"
        if (releaseType != "Release") {
            versionNameSuffix = myVersionName
        }
        buildConfigField(
            "String",
            "MY_VERSION_NAME",
            "\"$versionName$myVersionName\""
        )
        buildConfigField(
            "String",
            "RELEASE_TYPE",
            "\"$releaseType\""
        )
        buildConfigField(
            "boolean",
            "DISABLE_MEDIA_STORE_FILTER",
            "false"
        )
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("benchmarkRelease") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField(
                "boolean",
                "DISABLE_MEDIA_STORE_FILTER",
                "true"
            )
            matchingFallbacks += "release"
        }
        create("nonMinifiedRelease") {
            isMinifyEnabled = false
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField(
                "boolean",
                "DISABLE_MEDIA_STORE_FILTER",
                "true"
            )
            matchingFallbacks += "release"
        }
        create("profiling") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            isProfileable = true
            matchingFallbacks += "release"
        }
        create("userdebug") {
            isMinifyEnabled = false
            isProfileable = true
            isJniDebuggable = true
            isPseudoLocalesEnabled = true
            matchingFallbacks += "release"
        }
        debug {
            isPseudoLocalesEnabled = true
            applicationIdSuffix = ".debug"
        }
        forEach {
            it.vcsInfo {
                include = false
            }
            if (project.hasProperty("AKANE_RELEASE_KEY_ALIAS") || project.hasProperty("signing2")) {
                it.signingConfig = signingConfigs[if (project.hasProperty("signing2"))
                    "release2" else "release"]
            }
            it.isCrunchPngs = false // for reproducible builds TODO how much size impact does this have? where are the pngs from? can we use webp?
        }
    }

    sourceSets {
        getByName("debug") {
            // This does NOT remove src/debug/ source sets, hence "debug" is a superset of "userdebug"
            // TODO it seems this broke and that caused Reflections to crash
            java.directories += "src/userdebug/java"
            kotlin.directories += "src/userdebug/kotlin"
            resources.directories += "src/userdebug/resources"
            res.directories += "src/userdebug/res"
            assets.directories += "src/userdebug/assets"
            aidl.directories += "src/userdebug/aidl"
            renderscript.directories += "src/userdebug/renderscript"
            baselineProfiles.directories += "src/userdebug/baselineProfiles"
            jniLibs.directories += "src/userdebug/jniLibs"
            shaders.directories += "src/userdebug/shaders"
            mlModels.directories += "src/userdebug/mlModels"
        }
    }

    // https://gitlab.com/IzzyOnDroid/repo/-/issues/491
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

resourcePlaceholders {
    files.set(listOf("xml/shortcuts.xml"))
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs = listOf(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
            "-Xannotation-default-target=param-property", // can remove later
        )
    }
}

base {
    archivesName = "Gramophone-${android.defaultConfig.versionName}${android.defaultConfig.versionNameSuffix ?: ""}"
}

baselineProfile {
    dexLayoutOptimization = true
}

// https://stackoverflow.com/a/77745844
tasks.withType<PackageAndroidArtifact> {
    doFirst { appMetadata.asFile.orNull?.writeText("") }
}

aboutLibraries {
    offlineMode = true
    collect {
        configPath = file("config")
        filterVariants.add("release")
    }
    library {
        requireLicense = true
    }
    export {
        // Remove the "generated" timestamp to allow for reproducible builds
        excludeFields = listOf("generated")
    }
    license {
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
        allowedLicenses.addAll("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause")
    }
}

dependencies {
    implementation(project(":hificore"))
    implementation(project(":misc:alacdecoder"))
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.collection.ktx)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.midi)
    implementation(libs.androidx.media3.session)
    //implementation("androidx.paging:paging-runtime-ktx:3.2.1") TODO paged, partial, flow based library loading
    //implementation("androidx.paging:paging-guava:3.2.1") TODO do we have guava? do we need this?
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.transition.ktx) // <-- for predictive back TODO can we remove explicit dep now?
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.material)
    implementation(libs.fastscroll)
    implementation(libs.coil.compose)
    implementation(libs.hiddenapibypass)
    //noinspection GradleDependency newer versions need java.nio which is api 26+
    //implementation("com.github.albfernandez:juniversalchardet:2.0.3") TODO
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
    // --- below does not apply to release builds ---
    debugImplementation(libs.leakcanary.android)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    "userdebugImplementation"(libs.kotlin.reflect) // who thought String.invoke() is a good idea?????
    debugImplementation(libs.kotlin.reflect)
}

fun String.runCommand(
    workingDir: File = File(".")
): String = providers.exec {
    setWorkingDir(workingDir)
    commandLine(split(' '))
}.standardOutput.asText.get().removeSuffixIfPresent("\n")

fun readProperties(propertiesFile: File) = Properties().apply {
    propertiesFile.inputStream().use { fis ->
        load(fis)
    }
}
