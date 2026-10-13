import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.ksp)
    id("com.android.application")
    id("kotlin-android")
    kotlin("plugin.serialization")
    id("android-app-dependencies")
    id("test-app-dependencies")
    id("jacoco-app-dependencies")
}

repositories {
    mavenCentral()
    google()
}

fun generateGitBuild(): String {
    try {
        val processBuilder = ProcessBuilder("git", "describe", "--always")
        val output = File.createTempFile("git-build", "")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText().trim()
    } catch (_: Exception) {
        return "NoGitSystemAvailable"
    }
}

fun generateDate(): String {
    val stringBuilder: StringBuilder = StringBuilder()
    // showing only date prevents app to rebuild everytime
    stringBuilder.append(SimpleDateFormat("yyyy.MM.dd").format(Date()))
    return stringBuilder.toString()
}


android {
    namespace = "app.aaps.wear"

    defaultConfig {
        minSdk = Versions.wearMinSdk
        targetSdk = Versions.wearTargetSdk

        buildConfigField("String", "BUILDVERSION", "\"${generateGitBuild()}-${generateDate()}\"")
    }

    android {
        buildTypes {
            debug {
                enableUnitTestCoverage = true
                // Disable androidTest coverage, since it performs offline coverage
                // instrumentation and that causes online (JavaAgent) instrumentation
                // to fail in this project.
                enableAndroidTestCoverage = false
            }
        }
    }

    flavorDimensions.add("standard")
    productFlavors {
        create("full") {
            isDefault = true
            applicationId = "info.nightscout.androidaps"
            dimension = "standard"
            resValue("string", "app_name", "AAPS")
            versionName = Versions.appVersion
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
        }
        create("pumpcontrol") {
            applicationId = "info.nightscout.aapspumpcontrol"
            dimension = "standard"
            resValue("string", "app_name", "Pumpcontrol")
            versionName = Versions.appVersion + "-pumpcontrol"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_pumpcontrol"
        }
        create("aapsclient") {
            applicationId = "info.nightscout.aapsclient"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient")
            versionName = Versions.appVersion + "-aapsclient"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_yellowowl"
        }
        create("aapsclient2") {
            applicationId = "info.nightscout.aapsclient2"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient2")
            versionName = Versions.appVersion + "-aapsclient2"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_blueowl"
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

allprojects {
    repositories {
    }
}


dependencies {
    implementation(project(":shared:impl"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.legacy.support)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.wear)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.wear.watchface)
    implementation(libs.androidx.wear.watchface.complications.data)
    implementation(libs.androidx.wear.watchface.complications.datasource)
    implementation(libs.androidx.wear.watchface.complications.datasource.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.protobuf)

    implementation(libs.com.google.android.gms.playservices.wearable)
    implementation(files("${rootDir}/wear/libs/hellocharts-library-1.5.8.aar"))

    ksp(libs.com.google.dagger.android.processor)
    ksp(libs.com.google.dagger.compiler)
}
