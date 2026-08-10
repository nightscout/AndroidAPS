import com.android.build.api.variant.LibraryAndroidComponentsExtension
import kotlin.math.min

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    id("kotlinx-serialization")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("compose-test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {

    namespace = "app.aaps.core.interfaces"
    defaultConfig {
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)
    }

    buildFeatures {
        compose = true
    }
}

// Same generator as :core:keys and :core:ui, pointed at this module's strings. It lets the data
// enums here (ConcentrationType, InsulinType, CwfMetadataKey) carry a TextRef instead of an R.string
// Int, so they stop being Android-only. The strings themselves do not move, and AAPT keeps resolving
// them on Android exactly as before.
extensions.configure<LibraryAndroidComponentsExtension>("androidComponents") {
    onVariants { variant ->
        val taskProvider = tasks.register(
            "generate${variant.name.replaceFirstChar { it.uppercase() }}InterfacesStrings",
            GenerateKeyStringsTask::class.java
        ) {
            resDir.set(layout.projectDirectory.dir("src/main/res"))
            packageName.set("app.aaps.core.interfaces")
            owner.set("interfaces")
            objectName.set("InterfacesStrings")
            idsObjectName.set("InterfacesStringIds")
            reportFile.set(layout.buildDirectory.file("reports/interfacesStrings/${variant.name}-translations.txt"))
            // Set explicitly: addGeneratedSourceDirectory only applies a convention derived from the
            // task name, so both properties would land on one directory and the second file written
            // would delete the first.
            commonOutputDir.set(layout.buildDirectory.dir("generated/interfacesStrings/${variant.name}/common"))
            androidOutputDir.set(layout.buildDirectory.dir("generated/interfacesStrings/${variant.name}/android"))
        }
        variant.sources.kotlin?.addGeneratedSourceDirectory(taskProvider, GenerateKeyStringsTask::commonOutputDir)
        variant.sources.kotlin?.addGeneratedSourceDirectory(taskProvider, GenerateKeyStringsTask::androidOutputDir)
    }
}

dependencies {
    implementation(project(":core:data"))
    api(project(":core:keys"))

    // Dependency Injection
    api(libs.com.google.dagger.android)
    api(libs.com.google.dagger.hilt.android)

    api(libs.androidx.appcompat)
    api(libs.androidx.compose.ui)
    api(libs.androidx.documentfile)

    api(platform(libs.kotlinx.serialization.bom))
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.protobuf)

    api(libs.org.apache.commons.lang3)
    api(libs.net.danlew.android.joda)
    api(libs.kotlinx.datetime)

    //RxBus / RxJava base
    api(libs.io.reactivex.rxjava3.rxkotlin)

    testImplementation(libs.io.reactivex.rxjava3.rxandroid)
}