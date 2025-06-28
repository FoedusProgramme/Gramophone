@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.gradle.tasks.PackageAndroidArtifact
import org.gradle.kotlin.dsl.support.kotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.util.removeSuffixIfPresent
import java.util.Properties

val aboutLibsVersion = "12.2.3" // keep in sync with plugin version

plugins {
    id("com.android.application")
    id("androidx.baselineprofile")
    kotlin("android")
    kotlin("plugin.parcelize")
    kotlin("plugin.compose")
    id("com.mikepenz.aboutlibraries.plugin.android")
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
            freeCompilerArgs = listOf(
                "-Xno-param-assertions",
                "-Xno-call-assertions",
                "-Xno-receiver-assertions"
            )
        }
    }

    lint {
        lintConfig = file("lint.xml")
    }

    defaultConfig {
        applicationId = "org.akanework.gramophone"
        // Reasons to not support KK include me.zhanghai.android.fastscroll, WindowInsets for
        // bottom sheet padding, ExoPlayer requiring multidex, vector drawables and poor SD support
        // That said, supporting Android 5.0 costs tolerable amounts of tech debt and we plan to
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
    }

    buildTypes.forEach {
        (it as ApplicationBuildType).run {
            vcsInfo {
                include = false
            }
            if (project.hasProperty("AKANE_RELEASE_KEY_ALIAS") || project.hasProperty("signing2")) {
                signingConfig = signingConfigs[if (project.hasProperty("signing2"))
                    "release2" else "release"]
            }
            isCrunchPngs = false // for reproducible builds TODO how much size impact does this have? where are the pngs from? can we use webp?
        }
    }

    sourceSets {
        getByName("debug") {
            // This does NOT remove src/debug/ source sets, hence "debug" is a superset of "userdebug"
            java.srcDir("src/userdebug/java")
            kotlin.srcDir("src/userdebug/kotlin")
            resources.srcDir("src/userdebug/resources")
            res.srcDir("src/userdebug/res")
            assets.srcDir("src/userdebug/assets")
            aidl.srcDir("src/userdebug/aidl")
            renderscript.srcDir("src/userdebug/renderscript")
            baselineProfiles.srcDir("src/userdebug/baselineProfiles")
            jniLibs.srcDir("src/userdebug/jniLibs")
            shaders.srcDir("src/userdebug/shaders")
            mlModels.srcDir("src/userdebug/mlModels")
        }
    }

    // https://gitlab.com/IzzyOnDroid/repo/-/issues/491
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

base {
    archivesName = "Gramophone-${android.defaultConfig.versionName}${android.defaultConfig.versionNameSuffix ?: ""}"
}

androidComponents {
    onVariants(selector().withBuildType("release")) {
        // https://github.com/Kotlin/kotlinx.coroutines?tab=readme-ov-file#avoiding-including-the-debug-infrastructure-in-the-resulting-apk
        it.packaging.resources.excludes.addAll("META-INF/*.version", "DebugProbesKt.bin")
    }
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
        allowedLicenses.addAll("Apache-2.0", "MIT", "BSD-2-Clause", "LGPL")
    }
}

dependencies {
    implementation(project(":hificore"))
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3.adaptive:adaptive")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.collection:collection-ktx:1.5.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.2.0-beta02")
    //implementation("androidx.datastore:datastore-preferences:1.1.0-rc01") TODO don't abuse shared prefs
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.mediarouter:mediarouter:1.8.0")
    val media3Version = "1.7.1"
    implementation("androidx.media3:media3-common-ktx:$media3Version")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-midi:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    //implementation("androidx.navigation:navigation-fragment-ktx:2.7.7") TODO consider it
    //implementation("androidx.paging:paging-runtime-ktx:3.2.1") TODO paged, partial, flow based library loading
    //implementation("androidx.paging:paging-guava:3.2.1") TODO do we have guava? do we need this?
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.transition:transition-ktx:1.6.0") // <-- for predictive back TODO can we remove explicit dep now?
    implementation("com.mikepenz:aboutlibraries-compose-m3:$aboutLibsVersion")
    implementation("com.google.android.material:material:1.12.0")
    implementation("me.zhanghai.android.fastscroll:library:1.3.0")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    //noinspection GradleDependency newer versions need java.nio which is api 26+
    //implementation("com.github.albfernandez:juniversalchardet:2.0.3") TODO
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    "baselineProfile"(project(":baselineprofile"))
    // --- below does not apply to release builds ---
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    // Note: JAudioTagger is not compatible with Android 5, we can't ship it in app
    debugImplementation("net.jthink:jaudiotagger:3.0.1") // <-- for "SD Exploder"
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    "userdebugImplementation"(kotlin("reflect")) // who thought String.invoke() is a good idea?????
    debugImplementation(kotlin("reflect"))
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
