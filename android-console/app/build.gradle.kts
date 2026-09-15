plugins {
    id("com.android.application")
}

android {
    namespace = "dev.assay"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.assay"
        minSdk = 26
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
}
