// AGP is pinned to exactly the version hyle-design-system/gradle/libs.versions.toml declares
// (currently 8.9.1) — Gradle composite builds (see settings.gradle.kts's includeBuild) hard-fail
// if any project in the build graph uses a different AGP version. Confirmed by reading that file
// directly at the pinned submodule commit; re-check it before changing this version.
//
// The Kotlin Android plugin is declared explicitly here (it was previously implicit, relying on
// AGP 9.x's built-in Kotlin support) because AGP 8.9.1 predates that feature. Version matches
// hyle-design-system's own `kotlin` catalog entry (2.1.0) to keep one Kotlin compiler version
// across the composite build.
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}
