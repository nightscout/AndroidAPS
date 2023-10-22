plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlinx-serialization")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.core.nssdk"
}

dependencies {
    api(Libs.Squareup.Retrofit2.retrofit)
    api(Libs.Squareup.Retrofit2.adapterRxJava3)
    api(Libs.Squareup.Retrofit2.converterGson)
    api(Libs.Squareup.Okhttp3.okhttp)
    api(Libs.Squareup.Okhttp3.loggingInterceptor)
    api(Libs.Google.gson)
    api(Libs.jodaTimeAndroid)
    api(Libs.Rx.rxJava)
    //api(Libs.Rx.rxAndroid)
    api(Libs.Rx.rxKotlin)

    api(Libs.AndroidX.core)
    api(Libs.Kotlin.stdlibJdk8)
    api(Libs.KotlinX.coroutinesCore)
    api(Libs.KotlinX.coroutinesAndroid)
    api(Libs.KotlinX.coroutinesRx3)
    api(Libs.KotlinX.serializationJson)
}