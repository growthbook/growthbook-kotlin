import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("signing")
    id("org.jetbrains.dokka") version "1.4.20"
}

group = "io.growthbook.sdk"
version = "1.0.6"
val iOSBinaryName = "GrowthBook"


kotlin {

    val ktorVersion = "1.6.7"
    val serializationVersion = "1.3.2"

    android {
        publishLibraryVariants("release")
    }

//    jvm()

    val xcf = XCFramework()

    ios {
        binaries.framework {
            baseName = iOSBinaryName
            xcf.add(this)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
                implementation(
                    "org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-serialization:$ktorVersion")
                implementation("com.ionspin.kotlin:bignum:0.3.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(
                    "org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("io.ktor:ktor-client-serialization:$ktorVersion")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-android:$ktorVersion")
                implementation("androidx.startup:startup-runtime:1.1.0")
            }
        }
        val androidTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.13.2")
                implementation ("org.mockito:mockito-core:4.2.0")
            }
        }

        val iosMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-ios:$ktorVersion")
            }
        }

        val iosTest by getting

//        val jvmMain by getting
//        val jvmTest by getting

    }

}

android {
    compileSdk = 31
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = 26
        targetSdk = 31
    }
}

val dokkaOutputDir = "$buildDir/dokka"

tasks.dokkaHtml {
    outputDirectory.set(file(dokkaOutputDir))
}

val deleteDokkaOutputDir by tasks.register<Delete>("deleteDokkaOutputDirectory") {
    delete(dokkaOutputDir)
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    dependsOn(deleteDokkaOutputDir, tasks.dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaOutputDir)
}

val sonatypeUsername: String? = System.getenv("GB_SONATYPE_USERNAME")
val sonatypePassword: String? = System.getenv("GB_SONATYPE_PASSWORD")

publishing {
    repositories {
        maven {
            name="kotlin"
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

signing {
    useInMemoryPgpKeys(
        System.getenv("GPG_PRIVATE_KEY"),
        System.getenv("GPG_PRIVATE_PASSWORD")
    )
    sign(publishing.publications)
}


tasks.register("publishiOSXCFramework") {
    description = "Publish iOS framework to the Cocoa Repo"

    // Create Release Framework for Xcode
    dependsOn("assembleXCFramework")

    // Replace
    doLast {

        delete("$rootDir/XCFramework")

        copy {
            from("$buildDir/XCFrameworks/release")
            into("$rootDir/XCFramework")
        }

        val dir = File("$rootDir/$iOSBinaryName.podspec")
        val tempFile = File("$rootDir/$iOSBinaryName.podspec.new")

        val reader = dir.bufferedReader()
        val writer = tempFile.bufferedWriter()
        var currentLine: String?

        while (reader.readLine().also { currLine -> currentLine = currLine } != null) {
            if (currentLine?.trim()?.startsWith("spec.version") == true) {
                writer.write("    spec.version       = \"${version}\"" + System.lineSeparator())
            } else {
                writer.write(currentLine + System.lineSeparator())
            }
        }
        writer.close()
        reader.close()
        tempFile.renameTo(dir)
    }
}