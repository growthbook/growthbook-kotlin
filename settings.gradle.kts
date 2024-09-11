enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "GrowthBook-Kotlin"
include(":GrowthBook")
include(":Core")
include(":NetworkDispatcherKtor")
include(":NetworkDispatcherOkHttp")
