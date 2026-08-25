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

dependencies {
    api(platform(libs.kotlinx.coroutines.bom))
    api(libs.kotlinx.coroutines.core)
}
