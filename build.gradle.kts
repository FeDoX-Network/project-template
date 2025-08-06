/*
 * Copyright (c) 2025 Florian Ohldag. All rights reserved.
 *
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use is strictly prohibited
 * without prior written permission.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.time.Instant

plugins {
    java
    `maven-publish`
    id("com.gradleup.shadow") version "9.0.0-beta13"
}

group = "ovh.fedox"
version = "1.0.0"

val mainClassName = "FTemplate"
val purpose = "Template for FeDoX plugins"

val gitRef: String = System.getenv("CI_COMMIT_REF_NAME") ?: "local"
val isReleaseBuild: Boolean = !version.toString().contains("SNAPSHOT")

val localEnvFile = File("${System.getProperty("user.home")}/.gradle/reposilite-credentials.gradle.kts")
val reposiliteConfig = mutableMapOf<String, String>()

if (localEnvFile.exists()) {
    logger.info("Loading Reposilite configuration from: ${localEnvFile.absolutePath}")
    apply(from = localEnvFile.absolutePath)
    reposiliteConfig["url"] = extra.get("reposilite.url") as String? ?: "https://repo.fedox.ovh"
    reposiliteConfig["username"] = extra.get("reposilite.username") as String? ?: ""
    reposiliteConfig["password"] = extra.get("reposilite.password") as String? ?: ""
    reposiliteConfig["repository"] = extra.get("reposilite.repository") as String? ?: if (isReleaseBuild) "releases" else "snapshots"
} else {
    logger.warn("Local Reposilite configuration file not found: ${localEnvFile.absolutePath}")
    logger.warn("Create the file with your credentials for publishing.")
    reposiliteConfig["url"] = System.getenv("REPOSILITE_URL") ?: "https://repo.fedox.ovh"
    reposiliteConfig["username"] = System.getenv("REPOSILITE_USERNAME") ?: ""
    reposiliteConfig["password"] = System.getenv("REPOSILITE_PASSWORD") ?: ""
    reposiliteConfig["repository"] = System.getenv("REPOSILITE_REPOSITORY") ?: if (isReleaseBuild) "releases" else "snapshots"
}

// Java configuration
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

// Repository configuration
repositories {
    mavenCentral()
    maven {
        name = "JitPack"
        url = uri("https://jitpack.io")
    }
    maven {
        name = "Reposilite"
        url = uri("${reposiliteConfig["url"]}/releases")
        credentials {
            username = reposiliteConfig["username"]
            password = reposiliteConfig["password"]
        }
    }
}

// Dependencies
dependencies {
    // Compile-only dependencies
    compileOnly(libs.lombok)

    // Annotation processors
    annotationProcessor(libs.lombok)
}

val pluginNameLower = project.name.lowercase()
val mainClass = "${group}.${pluginNameLower}.${mainClassName}"

tasks.processResources {
    val props = mapOf(
        "NAME" to mainClassName,
        "VERSION" to project.version,
        "DESCRIPTION" to purpose,
        "AUTHOR" to "FeDoX Development",
        "AUTHORS" to "\"Florian Ohldag\", \"FeDoX Development\"",
        "WEBSITE" to "https://fedox.ovh",
        "MAIN" to mainClass,
    )

    inputs.properties(props)

    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }

    filesMatching("**/*.yml") {
        expand(props)
    }
    filesMatching("**/*.yaml") {
        expand(props)
    }
    filesMatching("**/*.properties") {
        expand(props)
    }
}

// Task configuration
tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    named<Jar>("jar") {
        enabled = false
        archiveClassifier.set("thin")
    }

    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("")
        mergeServiceFiles()

        // Exclude unnecessary files
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/LICENSE*")
        exclude("META-INF/NOTICE*")

        manifest {
            attributes(
                mapOf(
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version,
                    "Implementation-Vendor" to "Florian Ohldag",
                    "Built-By" to System.getProperty("user.name"),
                    "Built-JDK" to System.getProperty("java.version"),
                    "Built-Date" to Instant.now().toString(),
                    "Git-Ref" to gitRef
                )
            )
        }
    }

    build {
        dependsOn(shadowJar)
    }

    publish {
        dependsOn(shadowJar)

        doFirst {
            if (reposiliteConfig["username"].isNullOrEmpty() || reposiliteConfig["password"].isNullOrEmpty()) {
                throw GradleException("Reposilite credentials missing! Create ~/.gradle/reposilite-credentials.gradle.kts")
            }
        }
    }
}

// Publishing configuration for Reposilite
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            artifact(tasks.shadowJar)

            pom {
                name.set(project.name)
                description.set("${project.name} for FeDoX.ovh")
                url.set("https://github.com/FeDoX-Network/${project.name}")

                licenses {
                    license {
                        name.set("GNU Affero General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.en.html")
                    }
                }

                developers {
                    developer {
                        id.set("florian-ohldag")
                        name.set("Florian Ohldag")
                        email.set("contact@fedox.ovh")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/FeDoX-Network/${project.name}.git")
                    developerConnection.set("scm:git:ssh://github.com/FeDoX-Network/${project.name}.git")
                    url.set("https://github.com/FeDoX-Network/${project.name}")
                }
            }
        }
    }

    repositories {
        maven {
            name = "Reposilite"
            url = uri("${reposiliteConfig["url"]}/${reposiliteConfig["repository"]}")

            credentials {
                username = reposiliteConfig["username"]
                password = reposiliteConfig["password"]
            }

            // Reposilite-specific configuration
            isAllowInsecureProtocol = reposiliteConfig["url"]?.startsWith("http://") == true
        }
    }
}

// Custom Tasks
tasks.register("printReposiliteConfig") {
    group = "help"
    description = "Displays the current Reposilite configuration"
    doLast {
        println("=== Reposilite Configuration ===")
        println("URL: ${reposiliteConfig["url"]}")
        println("Repository: ${reposiliteConfig["repository"]}")
        println("Username: ${reposiliteConfig["username"]?.let { if (it.isNotEmpty()) "***" else "NOT SET" }}")
        println("Password: ${reposiliteConfig["password"]?.let { if (it.isNotEmpty()) "***" else "NOT SET" }}")
        println("Local config file: ${if (localEnvFile.exists()) "✓ Found" else "✗ Not found"}")
        println("Project version: ${project.version}")
        println("Git reference: $gitRef")
        println("Release build: $isReleaseBuild")
    }
}

tasks.register("createReposiliteConfig") {
    group = "setup"
    description = "Creates a template for the Reposilite configuration file"
    doLast {
        val configContent = """
            // Reposilite configuration for ${project.name}
            // File: ${localEnvFile.absolutePath}
            
            extra.set("reposilite.url", "https://repo.zflockii.de")
            extra.set("reposilite.username", "your-username")
            extra.set("reposilite.password", "your-password")
            extra.set("reposilite.repository", "releases") // or "snapshots"
        """.trimIndent()

        localEnvFile.parentFile.mkdirs()
        localEnvFile.writeText(configContent)

        println("Reposilite configuration file created: ${localEnvFile.absolutePath}")
        println("Please edit the file and insert your actual credentials!")
    }
}

// Gradle Wrapper
tasks.wrapper {
    gradleVersion = "8.5"
    distributionType = Wrapper.DistributionType.BIN
}
