# Gramophone Android Music Player

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Working Effectively

### Essential Setup (Required Before Any Build Attempts)
- Install JDK 21: `sudo apt update && sudo apt install -y openjdk-21-jdk`
- Set environment variables:
  ```bash
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  export PATH=$JAVA_HOME/bin:$PATH
  ```
- Initialize git submodules: `git submodule update --init --recursive` -- takes 2-3 minutes. NEVER CANCEL.
- Create package.properties file: `echo "releaseType=SelfBuilt" > package.properties`

### Build Commands (NETWORK DEPENDENT)
- **CRITICAL**: All builds require internet access to download Android Gradle Plugin from Google repositories
- **CRITICAL**: Gradle builds WILL FAIL without network access to `dl.google.com` and `repo.maven.apache.org`
- Debug build: `./gradlew :app:assembleDebug` -- takes 10-15 minutes when working. NEVER CANCEL. Set timeout to 30+ minutes.
- Release build: `./gradlew :app:assembleRelease` -- takes 15-20 minutes when working. NEVER CANCEL. Set timeout to 45+ minutes.
- Run tests: `./gradlew test` -- takes 5-10 minutes when working. NEVER CANCEL. Set timeout to 20+ minutes.

### Fastlane Setup and Usage
- Install Ruby dependencies: `sudo bundle install` (must use sudo due to system gem permissions)
- Fastlane version: `bundle exec fastlane --version`
- Available lanes:
  - `bundle exec fastlane android test` -- Runs Gradle tests
  - `bundle exec fastlane android buildrel` -- Build release APK
  - `bundle exec fastlane android googleplay` -- Deploy to Google Play Store
  - `bundle exec fastlane android gitrel` -- Build and push release to GitHub
  - `bundle exec fastlane android preprel` -- Prepare new release version

### Network Limitations
- **CRITICAL**: If network access to Google/Maven repositories is blocked, ALL Gradle builds will fail
- **CRITICAL**: If network access to JitPack.io is blocked, builds will fail with media3 dependency errors
- **CRITICAL**: You cannot build or test Android code without internet access
- Error pattern: "Plugin [id: 'com.android.application', version: 'X.X.X'] was not found"
- JitPack error pattern: "jitpack.io: No address associated with hostname"
- **WORKAROUND**: Document that builds require network access in any instructions you provide

## Validation Scenarios
- **NETWORK REQUIRED**: Cannot fully validate builds without internet access
- **BASIC VALIDATION**: Verify git submodules initialized, package.properties exists, JDK 21 installed
- **SYNTAX VALIDATION**: Use `./gradlew tasks --dry-run` to check basic Gradle setup (will still fail on plugin resolution)
- **LINT VALIDATION**: Cannot run Android lint without successful Gradle sync

## Repository Structure
```
.
├── app/                    # Main Android application module
├── alacdecoder/           # ALAC audio decoder module  
├── baselineprofile/       # Android baseline profile generation
├── hificore/              # Native audio core with C++ code
├── media3/                # Git submodule - Android Media3 library fork
├── fastlane/              # Deployment automation
├── .github/workflows/     # CI/CD pipelines
├── gradle/                # Gradle wrapper files
├── build.gradle.kts       # Root build configuration
├── settings.gradle.kts    # Gradle project settings
├── gradle.properties      # Gradle build properties
├── package.properties     # Required: Package type configuration
└── Gemfile               # Ruby/Fastlane dependencies
```

## Key Project Modules
- **app**: Main Android application (Kotlin, Compose UI)
- **hificore**: Native audio processing (C++/JNI)
- **alacdecoder**: ALAC audio format decoder
- **baselineprofile**: Performance optimization profiles
- **media3**: Forked ExoPlayer library (git submodule)

## Common Issues and Fixes
- **"Plugin not found" errors**: Requires internet access to Google repositories
- **"jitpack.io: No address associated with hostname"**: Requires internet access to JitPack repository
- **"git submodule" errors**: Run `git submodule update --init --recursive`
- **"package.properties" errors**: Create file with `echo "releaseType=SelfBuilt" > package.properties`
- **JDK version errors**: Ensure JDK 21 is installed and JAVA_HOME is set
- **Fastlane permission errors**: Use `sudo bundle install` for gem installation
- **Build cache issues**: Clear with `./gradlew clean --build-cache`

## GitHub Actions CI
- Workflow file: `.github/workflows/android.yml`
- Triggers: Push to beta branch, pull requests
- Requirements:
  - JDK 21 (temurin distribution)
  - Git submodules checkout
  - Package type: `releaseType=CI`
  - Signing keys for release builds (secrets)
- Build targets: `:app:assembleRelease` (main), `:app:assembleDebug` (PR)

## Development Workflow
- **Target branch**: `beta` (main development branch)
- **Build variant**: `debug` for development, `release` for distribution
- **Package types**: `SelfBuilt`, `CI`, `Release` (set in package.properties)
- **Native dependencies**: Requires NDK for hificore C++ module
- **Code style**: Kotlin with Compose UI, follows Android architecture guidelines

## Architecture Notes
- **UI**: Jetpack Compose with Material 3 design
- **Audio**: Custom native audio engine (hificore) + Media3
- **Database**: MediaStore integration for music discovery
- **Performance**: Baseline profiles for optimization
- **Testing**: JUnit + Android instrumentation tests
- **Release**: Fastlane automation for Play Store deployment

## Build Dependencies
- **Minimum**: JDK 21, Android SDK, internet access
- **Recommended**: Android Studio Electric Eel or later
- **Native**: Android NDK for C++ compilation
- **Ruby**: 3.2+ for Fastlane (bundle install required)
- **Git**: Submodule support essential

## Time Expectations
- **NEVER CANCEL**: All build operations require completion
- **Submodule init**: 2-3 minutes
- **Debug build**: 10-15 minutes (first time), 3-5 minutes (incremental)
- **Release build**: 15-20 minutes (first time), 5-10 minutes (incremental) 
- **Tests**: 5-10 minutes
- **Fastlane operations**: 10-30 minutes depending on lane
- **Baseline profile generation**: 20-30 minutes

## Critical Timeouts
- Set Gradle build timeouts to **45+ minutes** minimum
- Set test timeouts to **20+ minutes** minimum
- Set fastlane timeouts to **60+ minutes** minimum
- **NEVER use default timeouts** - they will cause premature cancellation

## Environment Setup Commands
```bash
# Essential setup sequence
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
git submodule update --init --recursive
echo "releaseType=SelfBuilt" > package.properties
sudo bundle install

# Verify setup
java -version  # Should show JDK 21
./gradlew --version  # Should work with JDK 21
bundle exec fastlane --version  # Should show fastlane version
```

## Network Requirements
- **CRITICAL**: Google Maven repositories access required
- **CRITICAL**: Maven Central repository access required  
- **CRITICAL**: JitPack.io repository access required for media3 dependencies
- **CRITICAL**: RubyGems.org access required for Fastlane
- **URLs needed**: `dl.google.com`, `repo.maven.apache.org`, `jitpack.io`, `rubygems.org`
- **Failure mode**: All builds fail with plugin resolution errors if network blocked
- **JitPack specific error**: "jitpack.io: No address associated with hostname" when accessing media3 dependencies