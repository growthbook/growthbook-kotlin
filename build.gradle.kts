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
    val signingKey = System.getenv("GPG_PRIVATE_KEY")
    val signingPassword = System.getenv("GPG_PRIVATE_PASSWORD")
    signing {
        // Only configure signing when a GPG key is present in the environment
        // (real releases export it via the shell / CI secrets). On PR runs the key
        // is absent, so signing is skipped and publishToMavenLocal can succeed
        // without a configured signatory.
        if (!signingKey.isNullOrBlank()) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publishing.publications)
        }
    }

    tasks
        .withType<AbstractPublishToMaven>()
        .configureEach {
            mustRunAfter(tasks.withType<Sign>())
        }

    // #250 guard: fail the build if the compiled jar of JVM bytecode is newer than our
    // target JDK. Implemented as a real task with the jar declared as an input — not a
    // `jvmJar` `doLast`, which Gradle skips whenever the jar task is UP-TO-DATE or
    // restored from cache — and wired into `check` and every publish task, so it
    // genuinely gates local/manual releases.
    val targetJdk = 17
    val expectedClassFileMajor = 44 + targetJdk // JDK 17 -> class file 61

    // The jar holding that bytecode is `jvmJar` in the multiplatform modules and plain
    // `jar` in the Kotlin/JVM-only ones, so the task name is picked per module. Hooked
    // on plugin application rather than read here: this `subprojects` block is evaluated
    // while the root project configures, before any subproject script has applied its
    // plugins, so asking `plugins.hasPlugin(...)` at this point would answer `false` for
    // every module.
    fun registerJvmBytecodeCheck(jarTaskName: String) {
        val verifyJvmBytecode = tasks.register("verifyJvmBytecode") {
            val jarFile = tasks.named<Jar>(jarTaskName).flatMap { it.archiveFile }
            inputs.file(jarFile).withPropertyName(jarTaskName)
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

    plugins.withId("org.jetbrains.kotlin.multiplatform") { registerJvmBytecodeCheck("jvmJar") }
    plugins.withId("org.jetbrains.kotlin.jvm") { registerJvmBytecodeCheck("jar") }
}

dependencies {
    subprojects.forEach {
        kover(it)
    }
}
