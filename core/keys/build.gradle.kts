import kotlin.math.min

plugins {
    alias(libs.plugins.android.library)
    id("android-module-dependencies")
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
