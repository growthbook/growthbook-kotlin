plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "io.growthbook.sdk"
// Required: release-please bumps this version using the marker comment. Don't remove it.
version = "1.0.0" // x-release-please-version

// OpenFeature's Kotlin SDK is pre-1.0, so a minor release can break providers.
val openFeatureVersion = "[0.9.0,0.10.0["

kotlin {
    // Pin every JVM-based compilation (incl. tests) to JDK 17 so the published -jvm
    // bytecode is Java 17 (class 61), not whatever JDK built it. OpenFeature's Kotlin SDK
    // itself targets Java 11, but this module depends on GrowthBook, which needs Java 17.
    jvmToolchain(17)

    androidTarget {
        publishLibraryVariants("release")
    }

    jvm()

    //noinspection UseTomlInstead
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":GrowthBook"))
                api("dev.openfeature:kotlin-sdk") {
                    version {
                        strictly(openFeatureVersion)
                        prefer("0.9.0")
                    }
                }
                implementation(libs.kotlinx.coroutines.core)
                implementation("org.jetbrains.kotlinx:atomicfu:0.33.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }
    }
}

android {
    compileSdk = 34
    namespace = "com.sdk.growthbook.openfeature"
    defaultConfig {
        minSdk = 21
    }
    buildTypes {
        debug {}
        release {}
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val dokkaOutputDir = "$buildDir/dokka"

tasks.dokkaHtml {
    outputDirectory.set(file(dokkaOutputDir))
}

/**
 * This task deletes older documents
 */
val deleteDokkaOutputDir by tasks.register<Delete>("deleteDokkaOutputDirectory") {
    delete(dokkaOutputDir)
}

/**
 * This task creates JAVA Docs for Release
 */
val javadocJar = tasks.register<Jar>("javadocJar") {
    dependsOn(deleteDokkaOutputDir, tasks.dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaOutputDir)
}

val sonatypeUsername: String? = System.getenv("GB_SONATYPE_USERNAME")
val sonatypePassword: String? = System.getenv("GB_SONATYPE_PASSWORD")

/**
 * Publishing Task for MavenCentral
 */
publishing {
    repositories {
        maven {
            name = "kotlin"
            val releasesRepoUrl =
                uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl =
                uri("https://ossrh-staging-api.central.sonatype.com/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = sonatypeUsername
                password = sonatypePassword
            }
        }
    }

    publications {
        withType<MavenPublication> {
            artifact(javadocJar)
            pom {
                name.set("kotlin")
                description.set("OpenFeature provider for the GrowthBook Kotlin SDK")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                url.set("https://github.com/growthbook/growthbook-kotlin")
                issueManagement {
                    system.set("Github")
                    url.set("https://github.com/growthbook/growthbook-kotlin/issues")
                }
                scm {
                    connection.set("https://github.com/growthbook/growthbook-kotlin.git")
                    url.set("https://github.com/growthbook/growthbook-kotlin")
                }
                developers {
                    developer {
                        name.set("GrowthBook")
                        email.set("sdk-dev@growthbook.io")
                    }
                }
            }
        }
    }
}
