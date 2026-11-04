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

// Shared reliability utility only (dev.aarso:crash-recovery), consumed as a pinned git submodule
// exactly like the rest of the constellation: Gradle substitutes the coordinate with the project
// in the included build, so no Maven registry is involved. Nothing else from that repo is used.
includeBuild("shared-libraries")
