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
    implementation(project(":core:data"))


    api(Libs.AndroidX.appCompat)
    api(Libs.AndroidX.preference)

    api(Libs.Dagger.androidSupport)

    api(Libs.KotlinX.serializationJson)
    api(Libs.KotlinX.serializationProtobuf)

    //TODO eliminate
    api(Libs.androidSvg)

    api(Libs.Apache.commonsLang3)

    //RxBus
    api(Libs.Rx.rxKotlin)
    testImplementation(Libs.Rx.rxAndroid)

    // TODO eliminate kapt from low level modules
    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}