import com.android.build.api.variant.LibraryAndroidComponentsExtension
import kotlin.math.min

plugins {
    alias(libs.plugins.android.library)
    id("android-module-dependencies")
    // Test only. composeKey() builds preference key names by hand instead of using String.format,
    // so it needs tests that pin the exact text it produces.
    id("test-module-dependencies")
}

android {
    namespace = "app.aaps.core.keys"
    defaultConfig {
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)  // Compatible with wear module
    }
}

// The key enums name their titles through the generated KeysStrings object rather than R.string,
// so this module stops carrying Android resource ids in its public API. KeysStringIds keeps the
// Android side resolving through AAPT. Both files come from one pass over res/values/strings.xml,
// so they cannot drift apart. See GenerateKeyStringsTask for the reasoning.
extensions.configure<LibraryAndroidComponentsExtension>("androidComponents") {
    onVariants { variant ->
        val taskProvider = tasks.register(
            "generate${variant.name.replaceFirstChar { it.uppercase() }}KeyStrings",
            GenerateKeyStringsTask::class.java
        ) {
            resDir.set(layout.projectDirectory.dir("src/main/res"))
            packageName.set("app.aaps.core.keys")
            objectName.set("KeysStrings")
            idsObjectName.set("KeysStringIds")
            reportFile.set(layout.buildDirectory.file("reports/keyStrings/${variant.name}-translations.txt"))
            // Set explicitly. addGeneratedSourceDirectory only applies a convention, and it derives
            // that convention from the task name, so both properties would land on the same
            // directory and the second file written would delete the first.
            commonOutputDir.set(layout.buildDirectory.dir("generated/keyStrings/${variant.name}/common"))
            androidOutputDir.set(layout.buildDirectory.dir("generated/keyStrings/${variant.name}/android"))
        }
        // Two directories rather than one: the names are platform neutral and will move to
        // commonMain when this module becomes multiplatform, while the id map stays on Android.
        variant.sources.kotlin?.addGeneratedSourceDirectory(taskProvider, GenerateKeyStringsTask::commonOutputDir)
        variant.sources.kotlin?.addGeneratedSourceDirectory(taskProvider, GenerateKeyStringsTask::androidOutputDir)
    }
}

dependencies {
    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
}
