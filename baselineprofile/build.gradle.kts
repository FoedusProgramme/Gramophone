@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("com.android.test")
	kotlin("android")
	id("androidx.baselineprofile")
}

android {
	namespace = "org.nift4.baselineprofile"
	compileSdk = 36

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlin {
		compilerOptions {
			jvmTarget = JvmTarget.JVM_17
			freeCompilerArgs = listOf(
				"-Xno-param-assertions",
				"-Xno-call-assertions",
				"-Xno-receiver-assertions"
			)
		}
	}

	defaultConfig {
		minSdk = 28
		targetSdk = 35

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	targetProjectPath = ":app"

	// This code creates the gradle managed device used to generate baseline profiles.
	// To use GMD please invoke generation through the command line:
	// ./gradlew :app:generateBaselineProfile
	testOptions.managedDevices.allDevices {
		create<ManagedVirtualDevice>("pixel6Api34") {
			device = "Pixel 6"
			apiLevel = 34
			systemImageSource = "aosp"
		}
	}
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
	managedDevices += "pixel6Api34"
	useConnectedDevices = false
}

dependencies {
	implementation("androidx.test.ext:junit:1.2.1")
	implementation("androidx.test.espresso:espresso-core:3.6.1")
	implementation("androidx.test.uiautomator:uiautomator:2.3.0")
	implementation("androidx.benchmark:benchmark-macro-junit4:1.3.4")
}

androidComponents {
	onVariants { v ->
		val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
		v.instrumentationRunnerArguments.put(
			"targetAppId",
			v.testedApks.map { artifactsLoader.load(it)?.applicationId!! }
		)
	}
}