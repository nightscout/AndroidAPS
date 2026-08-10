import com.android.build.api.variant.LibraryAndroidComponentsExtension
import kotlin.math.min

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("compose-test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.core.ui"
    defaultConfig {
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)
    }

    buildFeatures {
        compose = true
    }
}

// Same generator as :core:keys, pointed at this module's strings. It removes R.string from the
// Compose call sites so they stop being Android-only; the strings themselves do not move, and AAPT
// keeps resolving them on Android exactly as before.
extensions.configure<LibraryAndroidComponentsExtension>("androidComponents") {
    onVariants { variant ->
        val taskProvider = tasks.register(
            "generate${variant.name.replaceFirstChar { it.uppercase() }}UiStrings",
            GenerateKeyStringsTask::class.java
        ) {
            resDir.set(layout.projectDirectory.dir("src/main/res"))
            packageName.set("app.aaps.core.ui")
            owner.set("ui")
            objectName.set("UiStrings")
            idsObjectName.set("UiStringIds")
            reportFile.set(layout.buildDirectory.file("reports/uiStrings/${variant.name}-translations.txt"))
            // Set explicitly: addGeneratedSourceDirectory only applies a convention derived from the
            // task name, so both properties would land on one directory and the second file written
            // would delete the first.
            commonOutputDir.set(layout.buildDirectory.dir("generated/uiStrings/${variant.name}/common"))
            androidOutputDir.set(layout.buildDirectory.dir("generated/uiStrings/${variant.name}/android"))
        }
        variant.sources.kotlin?.addGeneratedSourceDirectory(taskProvider, GenerateKeyStringsTask::commonOutputDir)
        variant.sources.kotlin?.addGeneratedSourceDirectory(taskProvider, GenerateKeyStringsTask::androidOutputDir)
    }
}

dependencies {
    api(libs.androidx.appcompat)

    api(libs.com.google.android.material)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.activity.compose)
    api(libs.androidx.lifecycle.runtime.compose)

    api(libs.com.google.dagger.android)
    api(libs.com.google.dagger.android.support)

    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:data"))
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
