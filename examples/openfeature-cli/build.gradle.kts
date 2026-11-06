plugins {
    kotlin("jvm") version "2.3.0"
    application
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("io.growthbook.sdk:GrowthBookOpenFeature:1.0.0")
    implementation("io.growthbook.sdk:NetworkDispatcherOkHttp:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}
