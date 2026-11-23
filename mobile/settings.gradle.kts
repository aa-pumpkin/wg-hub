rootProject.name = "wg-hub"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// shared: all screens and logic, for Android now and iOS later.
// androidApp: the thin Android wrapper around it.
include(":shared", ":androidApp")
