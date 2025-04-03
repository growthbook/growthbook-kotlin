buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    //noinspection UseTomlInstead
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")

        val kotlinPluginsVersion = "2.0.0"
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinPluginsVersion")
        //noinspection GradleDependency
        classpath ("org.jetbrains.kotlin:kotlin-serialization:$kotlinPluginsVersion")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
    }
}

plugins {
    id("org.jetbrains.kotlinx.kover") version "0.5.0"
    id("signing")
    id("maven-publish")
}

subprojects {
    plugins.apply("signing")
    plugins.apply("maven-publish")
    signing {
        useInMemoryPgpKeys(
            System.getenv("GPG_PRIVATE_KEY"),
            System.getenv("GPG_PRIVATE_PASSWORD")
        )
    }
}
