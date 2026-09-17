plugins {
    // JVM-only, not multiplatform: both Redis clients are JVM libraries, so there is
    // nothing for the js/wasmJs/apple targets to compile here.
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "io.growthbook.sdk"
version = "1.0.0"

kotlin {
    // Pin the compilation to JDK 17 so the published bytecode is Java 17 (class 61), not
    // whatever JDK built it — the same treatment issue #250 applies to the other modules.
    // Matching the core SDK is what matters here: this module has `api(project(":GrowthBook"))`
    // and GrowthBook-jvm is class 61, so a lower target would only look like a promise it
    // cannot keep. Neither Redis client forces it down — Jedis 8 and Lettuce 7 are both
    // Java 8 bytecode.
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

dependencies {
    api(project(":GrowthBook"))
    // api, not implementation: CoroutineScope / CoroutineDispatcher are required parameters of
    // the public constructors and factories, so consumers need them on their compile classpath.
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Both Redis clients are optional: a consumer adds the one they use.
    compileOnly("redis.clients:jedis:8.0.0")
    compileOnly("io.lettuce:lettuce-core:7.7.0.RELEASE")

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("redis.clients:jedis:8.0.0")
    testImplementation("io.lettuce:lettuce-core:7.7.0.RELEASE")
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
        // Unlike the multiplatform modules, the Kotlin/JVM plugin does not register a
        // publication for us — it has to be created explicitly.
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(javadocJar)
            pom {
                name.set("kotlin")
                description.set(
                    "Redis adapters for the GrowthBook Kotlin SDK: shared, durable " +
                        "sticky bucket storage backed by Jedis or Lettuce."
                )
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
