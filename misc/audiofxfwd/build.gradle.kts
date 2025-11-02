plugins {
	id("java-library")
}
java {
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}
// This is a separate library to avoid exposing stubs to upstream classpath.
dependencies {
    // stub project that overrides SDK classes for @hide methods/subclasses in public classes
	compileOnly(project(":misc:audiofxstub"))
}