pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

rootProject.name = "openfeature-cli"

// Builds the provider from this repo; apps depend on the published artifacts instead.
includeBuild("../..")
