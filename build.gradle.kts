buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    //noinspection UseTomlInstead
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")

        val kotlinPluginsVersion = "2.1.20"
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
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
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
        sign(publishing.publications)
    }

    tasks
        .withType<AbstractPublishToMaven>()
        .configureEach {
            mustRunAfter(tasks.withType<Sign>())
        }
}
