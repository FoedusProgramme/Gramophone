import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.nift4.gramophone.hificore"
    compileSdk = 36

    defaultConfig {
        minSdk = 21

        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags("")
                // TODO remove this when NDK r28 is default in AGP.
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    lint {
        lintConfig = file("../app/lint.xml")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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
                "-Xno-receiver-assertions",
                "-Xannotation-default-target=param-property", // can remove later
                "-Xstring-concat=inline", // https://issuetracker.google.com/issues/250197571#comment29
            )
        }
    }
    buildFeatures {
        prefab = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.media3:media3-common:1.8.0")
    implementation("io.github.nift4.dlfunc:dlfunc:0.1.6")
    implementation(project(":misc:audiofxfwd"))
    // stub project that provides hidden SDK classes, which themselves depend on public SDK
    compileOnly(project(":misc:audiofxstub2"))
}