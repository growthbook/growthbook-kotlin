plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "io.growthbook.sdk"
version = "1.0.5"

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }

    jvm()

    //noinspection UseTomlInstead
    sourceSets {
        val okhttpVersion = "4.12.0"

        val commonMain by getting {
            dependencies {
                implementation(project(":Core"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                api("com.squareup.okhttp3:okhttp:$okhttpVersion")
                implementation("com.squareup.okhttp3:okhttp-sse:$okhttpVersion")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
    }
}

android {
    compileSdk = 34
    namespace = "com.sdk.growthbook.okhttp_network_dispatcher"
    defaultConfig {
        minSdk = 21
    }
    buildTypes {
        debug {}
        release {}
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
            val releasesRepoUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://ossrh-staging-api.central.sonatype.com/content/repositories/snapshots/")
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
                description.set("OkHttp network dispatcher for GrowthBook")
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
