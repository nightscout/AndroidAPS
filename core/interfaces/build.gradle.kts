plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-allopen")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
}

apply(from = "${project.rootDir}/core/main/android_dependencies.gradle")
apply(from = "${project.rootDir}/core/main/android_module_dependencies.gradle")
apply(from = "${project.rootDir}/core/main/allopen_dependencies.gradle")
apply(from = "${project.rootDir}/core/main/test_dependencies.gradle")
apply(from = "${project.rootDir}/core/main/jacoco_global.gradle")

android {

    namespace = "app.aaps.core.interfaces"
    defaultConfig {
        minSdk = 26  // for wear
    }
}

dependencies {
    implementation(project(":database:entities"))

    api(Libs.AndroidX.appCompat)
    api(Libs.AndroidX.preference)
    api(Libs.joda)

    api(Libs.Dagger.androidSupport)

    //Logger
    api(Libs.Logging.slf4jApi)
    api(Libs.Logging.logbackAndroid)

    api(Libs.Google.PlayServices.measurementApi)

    api(Libs.KotlinX.serializationJson)
    api(Libs.KotlinX.serializationProtobuf)

    api(Libs.androidSvg)
    api(Libs.Apache.commonsLang3)

    //RxBus
    api(Libs.Rx.rxJava)
    api(Libs.Rx.rxKotlin)
    api(Libs.Rx.rxAndroid)

    // WorkerClasses
    api(Libs.AndroidX.workRuntimeKtx)
}