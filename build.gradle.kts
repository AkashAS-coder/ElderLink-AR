// Top-level build file where you can add configuration options common to all sub-projects/modules.
@file:Suppress("DSL_SCOPE_VIOLATION")

import org.gradle.api.JavaVersion

plugins {
    // Defined in version catalog
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.services) apply false
}

buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:${libs.versions.agp.get()}")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        classpath("com.google.gms:google-services:4.4.0")
    }
}

// Simple clean task
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Configure common settings for all projects
allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_17.toString()
        }
    }
}

// Task: scanNativeLibs
// Scans resolved dependencies for AAR/JAR artifacts containing native .so files
tasks.register("scanNativeLibs") {
    group = "help"
    description = "Scan project dependencies for native libraries matching known problematic names"
    doLast {
        val targets = listOf("libimage_processing_util_jni.so", "libxeno_native.so")
        println("Scanning dependencies for: $targets")

        rootProject.subprojects.forEach { proj ->
            proj.configurations.forEach { conf ->
                try {
                    if (conf.isCanBeResolved) {
                        val files = conf.resolvedConfiguration.resolvedArtifacts.mapNotNull { it.file }
                        files.forEach { file ->
                            try {
                                if (file.extension in listOf("aar", "jar")) {
                                    java.util.zip.ZipFile(file).use { zip ->
                                        val entries = zip.entries()
                                        while (entries.hasMoreElements()) {
                                            val e = entries.nextElement()
                                            targets.forEach { t ->
                                                if (e.name.endsWith(t)) {
                                                    println("Found $t in ${file.name} (path: ${e.name}) -> project: ${proj.name}, configuration: ${conf.name}")
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (ignored: Exception) {
                            }
                        }
                    }
                } catch (ignored: Exception) {
                }
            }
        }
        println("Scan finished")
    }
}