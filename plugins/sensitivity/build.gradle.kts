plugins {
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    // Same reason as the :core modules, :pump:virtual and :plugins:smoothing.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    // Metro is a Kotlin COMPILER plugin, not KSP. Same one line as :plugins:smoothing.
    alias(libs.plugins.metro)
}

// Same generator as :plugins:smoothing, pointed at this module's strings. The strings themselves do
// not move, and AAPT keeps resolving them on Android exactly as before - this only adds a TextRef
// named view of them that common code can reach.
val generateSensitivityStrings = tasks.register<GenerateKeyStringsTask>("generateSensitivityStrings") {
    resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
    packageName.set("app.aaps.plugins.sensitivity")
    owner.set("sensitivity")
    objectName.set("SensitivityStrings")
    idsObjectName.set("SensitivityStringIds")
    reportFile.set(layout.buildDirectory.file("reports/sensitivityStrings/translations.txt"))
    // Set explicitly: addGeneratedSourceDirectory derives its convention from the task name, so both
    // properties would land on one directory and the second file written would delete the first.
    commonOutputDir.set(layout.buildDirectory.dir("generated/sensitivityStrings/common"))
    androidOutputDir.set(layout.buildDirectory.dir("generated/sensitivityStrings/android"))
}

kotlin {
    android {
        namespace = "app.aaps.plugins.sensitivity"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        androidResources { enable = true }
        // isIncludeAndroidResources is what makes Robolectric work - see :core:ui for the detail.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }

        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSensitivityStrings.flatMap { it.commonOutputDir })
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:utils"))
                implementation(project(":core:ui"))

                implementation(libs.androidx.collection)
                implementation(libs.cmp.runtime)
                implementation(libs.cmp.material.icons.extended)
            }
        }

        androidMain {
            // Android only: the string name to R.string id map.
            kotlin.srcDir(generateSensitivityStrings.flatMap { it.androidOutputDir })
        }

        // Hand written rather than taken from test-module-dependencies, which applies
        // com.android.library and so cannot be used here. Same approach as :plugins:smoothing.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                // The real org.json: isReturnDefaultValues makes the platform stub answer null rather
                // than throwing, which NPEs the shared profile fixtures. Same reason as :pump:virtual.
                implementation(libs.org.json.android)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

// :shared:tests is a flavoured Android library, so the host test classpath has to pick a flavour or
// resolution is ambiguous. Same pin as :plugins:smoothing.
listOf("androidHostTestCompileClasspath", "androidHostTestRuntimeClasspath").forEach { name ->
    configurations.named(name) {
        attributes {
            attribute(com.android.build.api.attributes.ProductFlavorAttr.of("standard"), objects.named("full"))
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
