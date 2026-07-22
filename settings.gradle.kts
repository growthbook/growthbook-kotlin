pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Auto-provisions the JDK 17 toolchain (jvmToolchain(17)) on machines that don't
    // have it installed, so the published bytecode is Java 17 (class 61) regardless of
    // the machine's default JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "GrowthBook"
include(":GrowthBook")
include(":Core")
include(":NetworkDispatcherKtor")
include(":NetworkDispatcherOkHttp")
include(":GrowthBookKotlinxSerialization")
