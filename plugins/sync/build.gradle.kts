plugins {
    id("com.android.library")
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
    implementation(project(":shared:impl"))
    implementation(project(":database:entities"))
    implementation(project(":database:impl"))
    implementation(project(":core:graphview"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))


    testImplementation(Libs.KotlinX.coroutinesTest)
    testImplementation(Libs.AndroidX.Work.testing)

    testImplementation(project(":shared:tests"))
    testImplementation(project(":implementation"))
    testImplementation(project(":plugins:aps"))
    androidTestImplementation(project(":shared:tests"))

    // OpenHuman
    api(Libs.Squareup.Okhttp3.okhttp)
    api(Libs.Squareup.Retrofit2.retrofit)
    api(Libs.AndroidX.browser)
    api(Libs.AndroidX.Work.runtimeKtx)
    api(Libs.AndroidX.gridLayout)
    api(Libs.Google.Android.material)

    // NSClient, Tidepool
    api(Libs.socketIo)
    api(Libs.Squareup.Okhttp3.loggingInterceptor)
    api(Libs.Squareup.Retrofit2.adapterRxJava3)
    api(Libs.Squareup.Retrofit2.converterGson)
    api(Libs.Google.gson)

    // DataLayerListenerService
    api(Libs.Google.Android.PlayServices.wearable)

    // Garmin
    api(Libs.connectiqSdk)
    androidTestImplementation(Libs.connectiqSdk)

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}