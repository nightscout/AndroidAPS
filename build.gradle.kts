import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/google")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.3")
        classpath("com.google.gms:google-services:4.4.0")
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.9")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files

        classpath(kotlin("gradle-plugin", version = Libs.Kotlin.kotlin))
        classpath(kotlin("allopen", version = Libs.Kotlin.kotlin))
        classpath(kotlin("serialization", version = Libs.Kotlin.kotlin))
    }
}

plugins {
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

allprojects {
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/google")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://jitpack.io")
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlin.ExperimentalUnsignedTypes",
                "-Xjvm-default=all"     //Support @JvmDefault
            )
            jvmTarget = "11"
        }
    }
    gradle.projectsEvaluated {
        tasks.withType<JavaCompile> {
            val compilerArgs = options.compilerArgs
            compilerArgs.add("-Xlint:deprecation")
            compilerArgs.add("-Xlint:unchecked")
        }
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "jacoco")
}

// Setup all reports aggregation
apply(from = "jacoco_aggregation.gradle.kts")

tasks.register<Delete>("clean").configure {
    delete(rootProject.buildDir)
}
