import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackOutput
import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "io.growthbook.sdk"
version = "5.0.0-alpha-2"

kotlin {

    val ktorVersion = "2.1.2"
    val kryptoVersion = "2.7.0"
    val kotlinxSerializationVersion = libs.versions.kotlinxSerialization.get()

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
                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
                implementation("com.ionspin.kotlin:bignum:0.3.3")
                implementation("com.soywiz.korlibs.krypto:krypto:$kryptoVersion")

                implementation(
                    "org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion"
                )
                api(project(":Core"))
                implementation(project(":GrowthBookKotlinxSerialization"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(
                    "org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion"
                )
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.startup:startup-runtime:1.1.1")
                implementation("com.soywiz.korlibs.krypto:krypto-android:$kryptoVersion")
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.13.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
                implementation("io.ktor:ktor-client-mock:$ktorVersion")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-java:$ktorVersion")
                implementation("com.soywiz.korlibs.krypto:krypto-jvm:$kryptoVersion")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation ("org.jetbrains.kotlin:kotlin-test-junit")
                implementation("com.soywiz.korlibs.krypto:krypto-jvm:$kryptoVersion")

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

        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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