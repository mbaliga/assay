plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "dev.assay"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.assay"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0-wp3"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") }
            // Optional: run Robolectric from a pre-downloaded android-all jar directory
            // (-ProbolectricDependencyDir=...), for hosts where Maven Central rate-limits.
            providers.gradleProperty("robolectricDependencyDir").orNull?.let { dir ->
                all {
                    it.systemProperty("robolectric.offline", "true")
                    it.systemProperty("robolectric.dependency.dir", dir)
                }
            }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        // API 36 is the latest Android SDK platform available on the stable SDK channel.
        // API 37 is intentionally deferred until it is published as a stable platform.
        // UseKtx is a syntax preference, not a correctness property. PluralsCandidate is
        // heuristic and this release intentionally ships an English-only string set.
        disable += setOf("OldTargetApi", "GradleDependency", "UseKtx", "PluralsCandidate")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation("dev.aarso:crash-recovery")

    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Test-only (never linked into the app): JUnit (EPL-1.0), Robolectric (MIT), Roborazzi and
    // MockWebServer (Apache-2.0).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.43.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
