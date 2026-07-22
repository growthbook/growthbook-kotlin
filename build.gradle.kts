buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    //noinspection UseTomlInstead
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")

        val kotlinPluginsVersion = "2.3.0"
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
                        minValue = 80
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

    // #250 guard: fail the build if the compiled `-jvm` jar contains bytecode newer
    // than our target JDK. Implemented as a real task with the jar declared as an
    // input — not a `jvmJar` `doLast`, which Gradle skips whenever `jvmJar` is
    // UP-TO-DATE or restored from cache — and wired into `check` and every publish
    // task, so it genuinely gates local/manual releases.
    val targetJdk = 17
    val expectedClassFileMajor = 44 + targetJdk // JDK 17 -> class file 61
    val verifyJvmBytecode = tasks.register("verifyJvmBytecode") {
        val jarFile = tasks.named<Jar>("jvmJar").flatMap { it.archiveFile }
        inputs.file(jarFile).withPropertyName("jvmJar")
        doLast {
            val jar = jarFile.get().asFile
            java.util.zip.ZipFile(jar).use { zf ->
                zf.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".class") }
                    .forEach { entry ->
                        val major = java.io.DataInputStream(zf.getInputStream(entry)).use { dis ->
                            dis.skipBytes(6)        // magic (4 bytes) + minor version (2 bytes)
                            dis.readUnsignedShort() // major version = class-file bytes 6–7
                        }
                        check(major == expectedClassFileMajor) {
                            "${entry.name} in ${jar.name}: class file version $major != " +
                                "$expectedClassFileMajor (JDK $targetJdk) — see issue #250"
                        }
                    }
            }
        }
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyJvmBytecode) }
    tasks.withType<AbstractPublishToMaven>().configureEach { dependsOn(verifyJvmBytecode) }
}

dependencies {
    subprojects.forEach {
        kover(it)
    }
}
