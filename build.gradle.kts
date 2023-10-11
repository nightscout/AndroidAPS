import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    // extra is duplicated to Versions here until full migration to kts
    extra.apply {
        set("kotlin_version", "1.9.10")
        set("core_version", "1.12.0")
        set("rxjava_version", "3.1.7")
        set("rxandroid_version", "3.0.2")
        set("rxkotlin_version", "3.0.1")
        set("room_version", "2.5.2")
        set("lifecycle_version", "2.6.2")
        set("dagger_version", "2.48.1")
        set("coroutines_version", "1.7.3")
        set("activity_version", "1.8.0")
        set("fragmentktx_version", "1.6.1")
        set("ormLite_version", "4.46")
        set("gson_version", "2.10.1")
        set("nav_version", "2.7.4")
        set("appcompat_version", "1.6.1")
        set("material_version", "1.10.0")
        set("gridlayout_version", "1.0.0")
        set("constraintlayout_version", "2.1.4")
        set("preferencektx_version", "1.2.1")
        set("commonscodec_version", "1.16.0")
        set("guava_version", "32.1.3-jre")
        set("jodatime_version", "2.12.5")
        set("work_version", "2.8.1")
        set("tink_version", "1.10.0")
        set("json_version", "20230618")
        set("joda_version", "2.12.5")
        set("swipe_version", "1.1.0")

        set("junit_version", "4.13.2")
        set("junit_jupiter_version", "5.10.0")
        set("mockito_version", "5.6.0")
        set("dexmaker_version", "1.2")
        set("retrofit2_version", "2.9.0")
        set("okhttp3_version", "4.11.0")
        set("byteBuddy_version", "1.12.8")

        set("androidx_junit_version", "1.1.5")
        set("androidx_rules_version", "1.5.0")

        set("rxandroidble_version", "1.12.1")
        set("replayshare_version", "2.2.0")

        set("wearable_version", "2.9.0")
        set("play_services_wearable_version", "18.1.0")
        set("play_services_location_version", "21.0.1")

        set("kotlinx_datetime_version", "0.4.1")
        set("kotlinx_serialization_version", "1.6.0")

    }
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.2")
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
    id("org.jetbrains.kotlin.android") version Libs.Kotlin.kotlin apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.google.com")
        maven("https://jitpack.io")
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf(
                "-opt-in=kotlin.RequiresOptIn",
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

// Setup all al reports aggregation
apply(from = "jacoco_project.gradle")

tasks.register<Delete>("clean").configure {
    delete(rootProject.buildDir)
}
