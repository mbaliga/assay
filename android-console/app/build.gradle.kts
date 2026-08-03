plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.assay"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.assay"
        // Was 26. Raised to 31 because dev.aarso:hyle (hyle-design-system/hyle/build.gradle.kts)
        // declares minSdk = 31, and Gradle's manifest merger fails a build where the app's
        // minSdk is lower than a dependency's. This drops device support from Android 8.0 to
        // Android 12 — a real, user-visible consequence of taking the Hyle dependency, not a
        // cosmetic one. Flagged in the PR body for a human call on whether that trade is wanted.
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    buildFeatures {
        buildConfig = true
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

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        // API 36 is the latest Android SDK platform available on the stable SDK channel.
        // API 37 is intentionally deferred until it is published as a stable platform.
        // UseKtx is a syntax preference, not a correctness property. PluralsCandidate is
        // heuristic and this first release intentionally ships an English-only string set.
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
    implementation("androidx.activity:activity-ktx:1.13.0")
    // Resolved against the ":hyle" project inside the hyle-design-system composite build
    // (settings.gradle.kts's includeBuild) by group:name:version coordinate matching — see
    // hyle-design-system/hyle/build.gradle.kts for why the version must match exactly.
    implementation("dev.aarso:hyle:0.2.0")
}
