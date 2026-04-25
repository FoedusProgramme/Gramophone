// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    val agpVersion = "9.2.0"
    id("com.android.application") version agpVersion apply false
    id("com.android.built-in-kotlin") version agpVersion apply false
	id("com.android.library") version agpVersion apply false
	id("com.android.test") version agpVersion apply false
	id("androidx.baselineprofile") version "1.5.0-alpha03" apply false
    val kotlinVersion = "2.3.0"
	kotlin("android") version kotlinVersion apply false
    kotlin("plugin.parcelize") version kotlinVersion apply false
    kotlin("plugin.compose") version kotlinVersion apply false
    val aboutLibsVersion = "14.0.0-b02"
    id("com.mikepenz.aboutlibraries.plugin") version aboutLibsVersion apply false
    id("com.mikepenz.aboutlibraries.plugin.android") version aboutLibsVersion apply false
    id("com.osacky.doctor") version "0.12.1"
    id("pt.jcosta.resourceplaceholders") version "0.11.2" apply false
}

doctor {
    javaHome {
        ensureJavaHomeMatches.set(false)
        ensureJavaHomeIsSet.set(false)
    }
}

tasks.withType(JavaCompile::class.java) {
    options.compilerArgs.add("-Xlint:all")
}
