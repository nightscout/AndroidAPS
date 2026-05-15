import kotlin.math.min

plugins {
    alias(libs.plugins.android.library)
    kotlin("plugin.allopen")
    id("android-module-dependencies")
    id("all-open-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.core.utils"
    defaultConfig {
        minSdk = min(Versions.minSdk, Versions.wearMinSdk)
    }
}

dependencies {

    api(libs.net.danlew.android.joda)
    api(platform(libs.kotlinx.serialization.bom))
    api(libs.kotlinx.serialization.json)

    //Firebase
    api(platform(libs.com.google.firebase.bom))
    api(libs.com.google.firebase.analytics)
    api(libs.com.google.firebase.crashlytics)

    //CryptoUtil
    api(libs.com.madgag.spongycastle)
    api(libs.com.google.crypto.tink)

    //WorkManager
    api(libs.androidx.work.runtime) // DataWorkerStorage

    // ProcessLifecycleOwner for DeferredForegroundStart
    implementation(libs.androidx.lifecycle.process)

    api(libs.com.google.dagger.android) // for javax.inject annotations
    api(libs.com.google.dagger.android.support)
}