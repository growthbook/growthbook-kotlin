import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("signing")
    id("org.jetbrains.dokka") version "1.4.20"
}

group = "io.github.stevenleadbeater"
val iOSBinaryName = "GrowthBook"

kotlin {

    val ktorVersion = "1.6.7"
    val serializationVersion = "1.3.2"

    android {
        publishLibraryVariants("release")
    }

//    jvm()


    val xcf = XCFramework()
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = iOSBinaryName
            xcf.add(this)
        }
    }

    watchos {
        binaries.framework {
            baseName = iOSBinaryName
            xcf.add(this)
        }
    }

    tvos {
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
                    "org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion"
                )
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
                    "org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion"
                )
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
                implementation("org.mockito:mockito-core:4.2.0")
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        val tvosMain by getting

        val watchosMain by getting

        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("io.ktor:ktor-client-ios:$ktorVersion")
            }
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)

            tvosMain.dependsOn(this)

            watchosMain.dependsOn(this)

        }
        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting

        val iosTest by creating {
            dependsOn(commonTest)
            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)
        }


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
    useInMemoryPgpKeys(
        System.getenv("GPG_PRIVATE_KEY"),
        System.getenv("GPG_PRIVATE_PASSWORD")
    )
    sign(publishing.publications)
}

/**
 * This task execution requires - pod trunk to be setup
 * pod trunk register <GIT_EMAIL_HAVING_ACCESS_TO_REPO> '<NAME>' --description='<DESCRIPTION>
 */
tasks.register("prepareReleaseOfiOSXCFramework") {
    description = "Publish iOS framework to the Cocoa Repo"

    // Create Release Framework for Xcode
    dependsOn("assembleXCFramework", "packageDistribution")

    doLast {

        // Update Podspec Version
        val poddir = File("$rootDir/$iOSBinaryName.podspec")
        val podtempFile = File("$rootDir/$iOSBinaryName.podspec.new")

        val podreader = poddir.bufferedReader()
        val podwriter = podtempFile.bufferedWriter()
        var podcurrentLine: String?

        while (podreader.readLine().also { currLine -> podcurrentLine = currLine } != null) {
            if (podcurrentLine?.trim()?.startsWith("spec.version") == true) {
                podwriter.write("    spec.version       = \"${version}\"" + System.lineSeparator())
            } else if (podcurrentLine?.trim()?.startsWith("spec.source") == true) {
                podwriter.write("    spec.source       = { :http => \"https://github.com/growthbook/growthbook-kotlin/releases/download/${version}/${iOSBinaryName}.xcframework.zip\"}" + System.lineSeparator())
            } else {
                podwriter.write(podcurrentLine + System.lineSeparator())
            }
        }
        podwriter.close()
        podreader.close()
        podtempFile.renameTo(poddir)

        // Update Cartfile Version
        val cartdir = File("$rootDir/Carthage/$iOSBinaryName.json")
        val carttempFile = File("$rootDir/Carthage/$iOSBinaryName.json.new")

        val cartreader = cartdir.bufferedReader()
        val cartwriter = carttempFile.bufferedWriter()
        var cartcurrentLine: String?

        while (cartreader.readLine().also { currLine -> cartcurrentLine = currLine } != null) {
            if (cartcurrentLine?.trim()?.startsWith("{") == true) {
                cartwriter.write("{" + System.lineSeparator())
                cartwriter.write("    \"${version}\":\"https://github.com/growthbook/growthbook-kotlin/releases/download/${version}/${iOSBinaryName}.xcframework.zip\"," + System.lineSeparator())
            } else if (cartcurrentLine?.trim()?.startsWith("\"${version}\"") == true) {
                continue
            } else {
                cartwriter.write(cartcurrentLine + System.lineSeparator())
            }
        }
        cartwriter.close()
        cartreader.close()
        carttempFile.renameTo(cartdir)

        // Update Package.swift Version

        // Calculate Checksum
        val checksumValue: String = org.apache.commons.io.output.ByteArrayOutputStream()
            .use { outputStream ->
                // Calculate checksum
                project.exec {
                    workingDir = File("$rootDir")
                    commandLine("swift", "package", "compute-checksum", "${iOSBinaryName}.xcframework.zip")
                    standardOutput = outputStream
                }

                outputStream.toString()
            }


        val spmdir = File("$rootDir/Package.swift")
        val spmtempFile = File("$rootDir/Package.swift.new")

        val spmreader = spmdir.bufferedReader()
        val spmwriter = spmtempFile.bufferedWriter()
        var spmcurrentLine: String?

        while (spmreader.readLine().also { currLine -> spmcurrentLine = currLine } != null) {
            if (spmcurrentLine?.trim()?.startsWith("url") == true) {
                spmwriter.write("    url: \"https://github.com/growthbook/growthbook-kotlin/releases/download/${version}/${iOSBinaryName}.xcframework.zip\"," + System.lineSeparator())
            } else if (spmcurrentLine?.trim()?.startsWith("checksum") == true) {
                spmwriter.write("    checksum: \"${checksumValue.trim()}\"" + System.lineSeparator())
            } else {
                spmwriter.write(spmcurrentLine + System.lineSeparator())
            }
        }
        spmwriter.close()
        spmreader.close()
        spmtempFile.renameTo(spmdir)
    }
}

/**
 * Task to create zip for XCFramework
 * To be used by Carthage for Distribution as Binary
 */
tasks.register<Zip>("packageDistribution") {

    // Delete existing XCFramework
    delete("$rootDir/XCFramework")

    // Replace XCFramework File at root from Build Directory
    copy {
        from("$buildDir/XCFrameworks/release")
        into("$rootDir/XCFramework")
    }

    // Delete existing ZIP, if any
    delete("$rootDir/${iOSBinaryName}.xcframework.zip")
    // ZIP File Name - as per Carthage Nomenclature
    archiveFileName.set("${iOSBinaryName}.xcframework.zip")
    // Destination for ZIP File
    destinationDirectory.set(layout.projectDirectory.dir("../"))
    // Source Directory for ZIP
    from(layout.projectDirectory.dir("../XCFramework"))
}

tasks.register("publishiOSXCFramework") {

    // Publish to CocoaPod Trunk
    project.exec {
        workingDir = File("$rootDir")
        commandLine("pod", "trunk", "push", "${iOSBinaryName}.podspec", "--verbose", "--allow-warnings").standardOutput
    }
}
