import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackOutput
import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "io.growthbook.sdk"
version = "5.0.0-alpha-3"

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

    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    //noinspection UseTomlInstead
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":Core"))

                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
                implementation(libs.kotlinx.coroutines.core)
                implementation("com.ionspin.kotlin:bignum:0.3.3") // used in hash calculation
                implementation("com.soywiz.korlibs.krypto:krypto:2.7.0") // encryption/decryption

                implementation(libs.kotlinx.serialization.json)
                implementation(project(":GrowthBookKotlinxSerialization"))
            }
        }
        // val commonTest by getting {}
        val androidMain by getting {
            dependencies {
                implementation("androidx.startup:startup-runtime:1.2.0")
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.13.2")
            }
        }

        val jvmMain by getting {}
        val jvmTest by getting {
            dependencies {
                implementation ("org.jetbrains.kotlin:kotlin-test-junit")

                // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-test
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

                implementation("io.mockk:mockk:1.13.16")
            }
        }
    }
}

android {
    compileSdk = 34
    namespace = "com.sdk.growthbook"
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = 21
    }
    buildTypes {
        debug {}
        release {}
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
                description.set("Powerful A/B testing SDK for Kotlin")
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
                        name.set("Nicholas Pearson")
                        email.set("nicholaspearson918@gmail.com")
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
    sign(publishing.publications)
}