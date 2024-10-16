plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.sync"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))
    implementation(project(":shared:impl"))


    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(Libs.AndroidX.Work.testing)

    testImplementation(project(":shared:tests"))
    testImplementation(project(":implementation"))
    testImplementation(project(":plugins:aps"))
    androidTestImplementation(project(":shared:tests"))

    // OpenHuman
    api(libs.com.squareup.okhttp3.okhttp)
    api(libs.com.squareup.retrofit2.retrofit)
    api(Libs.AndroidX.browser)
    api(Libs.AndroidX.Work.runtimeKtx)
    api(Libs.AndroidX.gridLayout)
    api(libs.com.google.android.material)

    // NSClient, Tidepool
    api(Libs.socketIo)
    api(libs.com.squareup.okhttp3.logging.interceptor)
    api(libs.com.squareup.retrofit2.adapter.rxjava3)
    api(libs.com.squareup.retrofit2.converter.gson)
    api(libs.com.google.code.gson)

    // DataLayerListenerService
    api(libs.com.google.android.gms.playservices.wearable)

    // Garmin
    api(Libs.connectiqSdk)
    androidTestImplementation(Libs.connectiqSdk)

    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}