pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "assay-android-console"
include(":app")

// Hyle Design System — the constellation's shared design system. Sharing mechanism D-A:
// git submodule (android-console/hyle-design-system) + Gradle includeBuild, never vendored
// source, never published to a registry. `implementation("dev.aarso:hyle:0.2.0")` in
// app/build.gradle.kts is substituted against the ":hyle" project inside this composite
// build by Gradle's module-coordinate matching (group "dev.aarso", name "hyle").
//
// D-Q constraint: this repo's own Android Gradle Plugin version (see build.gradle.kts) must
// be pinned to EXACTLY the AGP version hyle-design-system's build uses, or Gradle refuses the
// composite build ("Using multiple versions of the Android Gradle plugin ... is not allowed").
// Verify hyle-design-system/gradle/libs.versions.toml's `agp` entry before bumping either side.
includeBuild("hyle-design-system")
