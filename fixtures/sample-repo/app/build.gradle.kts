plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.assaysample"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.assaysample"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Lock only what ships in the app, so gradle.lockfile (which OSV-Scanner reads) lists the
// app's real runtime dependencies and not Android Gradle Plugin internals.
configurations.matching { it.name in setOf("debugRuntimeClasspath", "releaseRuntimeClasspath") }
    .configureEach { resolutionStrategy.activateDependencyLocking() }

dependencies {
    implementation(libs.jackson.databind)
}
