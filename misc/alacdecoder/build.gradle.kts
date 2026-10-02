plugins {
    id("com.android.library")
}

android {
    namespace = "org.nift4.alacdecoder"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
    }

    lint {
        lintConfig = file("../../app/lint.xml")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    enableKotlin = false
}

dependencies {
    implementation("androidx.annotation:annotation:1.11.0")
    implementation("androidx.media3:media3-exoplayer")
}
