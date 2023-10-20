plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {

    namespace = "app.aaps.core.interfaces"
    defaultConfig {
        minSdk = Versions.wearMinSdk  // for wear
    }
}

dependencies {
    implementation(project(":database:entities"))

    api(Libs.AndroidX.appCompat)
    api(Libs.AndroidX.preference)
    api(Libs.jodaTimeAndroid)

    api(Libs.Dagger.androidSupport)

    //Logger
    api(Libs.Logging.slf4jApi)
    api(Libs.Logging.logbackAndroid)

    api(Libs.Google.Android.PlayServices.measurementApi)

    api(Libs.KotlinX.serializationJson)
    api(Libs.KotlinX.serializationProtobuf)

    api(Libs.androidSvg)
    api(Libs.Apache.commonsLang3)

    //RxBus
    api(Libs.Rx.rxJava)
    api(Libs.Rx.rxKotlin)
    api(Libs.Rx.rxAndroid)

    // WorkerClasses
    api(Libs.AndroidX.Work.runtimeKtx)

    // TODO eliminate kapt from low level modules
    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}