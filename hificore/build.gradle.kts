import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("com.android.built-in-kotlin")
}

android {
    namespace = "org.nift4.gramophone.hificore"
    compileSdk = 37
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 23

        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags("")
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
    buildFeatures {
        prefab = true
    }
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

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.media3:media3-common:1.11.0")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("io.github.nift4.dlfunc:dlfunc:0.1.6")
    implementation(project(":misc:audiofxfwd"))
    // stub project that provides hidden SDK classes, which themselves depend on public SDK
    compileOnly(project(":misc:audiofxstub2"))
}