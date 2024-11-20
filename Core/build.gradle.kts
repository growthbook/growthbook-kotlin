import com.android.build.gradle.internal.utils.publishingFeatureOptIn
import org.gradle.kotlin.dsl.support.kotlinCompilerOptions
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackOutput
import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("signing")
    id("org.jetbrains.dokka") version "1.8.10"
}

group = "io.growthbook.sdk"
version = "1.0.2"

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }

    js {
        yarn.lockFileDirectory = file("kotlin-js-store")
        browser {
            commonWebpackConfig {
                output = KotlinWebpackOutput(
                    library = project.name,
                    libraryTarget = KotlinWebpackOutput.Target.UMD,
                    globalObject = KotlinWebpackOutput.Target.WINDOW
                )
            }
        }
    }

    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
    }
}

android {
    compileSdk = 34
    namespace = "com.sdk.growthbook.core"
    defaultConfig {
        minSdk = 21
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
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
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
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
                description.set("Core module of GrowthBook Kotlin SDK")
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
                        name.set("Bohdan Kim")
                        email.set("user576g@gmail.com")
                    }
                }
            }
        }
    }
}

/**
 * Signing JAR using GPG Keys
 */
signing {
    useInMemoryPgpKeys(
        System.getenv("GPG_PRIVATE_KEY"),
        System.getenv("GPG_PRIVATE_PASSWORD")
    )
    sign(publishing.publications)
}