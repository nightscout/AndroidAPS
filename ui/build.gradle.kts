plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    // Same reason as :core:graph, :core:ui, :core:objects and the rest.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.metro)
}

metro {
    interop {
        // PermissionsViewModel still reads Hilt's @ApplicationContext qualifier. Everything else in this
        // module is Metro now, but without interop that one qualifier would be ignored silently.
        includeDagger()
    }
}

kotlin {
    android {
        namespace = "app.aaps.ui"
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

    sourceSets {
        // Everything is still Android: the widgets are RemoteViews, and Glance, WorkManager and OkHttp
        // are Android only. Screens move to commonMain one at a time from here, the way :core:ui did.
        androidMain {
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:graph"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:ui"))

                api(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.activity.compose)
                api(libs.androidx.compose.material3)
                api(libs.androidx.compose.material.icons.extended)
                api(libs.androidx.compose.runtime)
                api(libs.androidx.lifecycle.runtime.compose)
                api(libs.androidx.ui.tooling.preview)
                // Was debugImplementation; the multiplatform library target has no build types.
                implementation(libs.androidx.ui.tooling)
                implementation(libs.sh.calvin.reorderable)
                implementation(libs.androidx.glance.appwidget)
                implementation(libs.androidx.work.runtime)
                implementation(libs.androidx.core)
                api(libs.kotlinx.datetime)

                api(libs.com.squareup.okhttp3.okhttp)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                // The real org.json, not the android.jar stub: with isReturnDefaultValues the stub
                // answers null and the failure surfaces far away, as an NPE inside a test fixture.
                implementation(libs.org.json.android)
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.compose.ui.test.junit4)
                implementation(libs.org.robolectric)
                implementation(libs.androidx.compose.ui.test.manifest)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

// :shared:tests is a flavoured Android library and a multiplatform module has no flavours of its own,
// so resolution would be ambiguous. Pin the same flavour the app builds with - neither module has
// flavour specific sources, so this only picks a variant, it does not change code.
// Same pin as :implementation and :plugins:main.
listOf(
    "androidCompileClasspath",
    "androidRuntimeClasspath",
    "androidHostTestCompileClasspath",
    "androidHostTestRuntimeClasspath"
).forEach { name ->
    configurations.named(name) {
        attributes {
            attribute(com.android.build.api.attributes.ProductFlavorAttr.of("standard"), objects.named("full"))
        }
    }
}
