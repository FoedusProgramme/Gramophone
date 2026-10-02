plugins {
    id("com.android.library")
}

android {
    namespace = "org.nift4.audiofxstub2"
    compileSdk = 37
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    enableKotlin = false
}
