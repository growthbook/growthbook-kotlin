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
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinPluginsVersion")
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
    id("signing")
    id("maven-publish")
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
}

kover {
    reports {
        total {
            xml {
                onCheck = true
            }
            html {
                onCheck = true
            }
            verify {
                rule {
                    bound {
                        minValue = 60
                    }
                }
            }
        }
    }
}

subprojects {
    plugins.apply("signing")
    plugins.apply("maven-publish")
    plugins.apply("org.jetbrains.kotlinx.kover")
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

dependencies {
    subprojects.forEach {
        kover(it)
    }
}
