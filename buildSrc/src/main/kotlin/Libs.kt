@Suppress("SpellCheckingInspection")
object Libs {

    object Kotlin {

        const val kotlin = "1.9.10"

    }

    object KotlinX {

        private const val serialization = "1.6.0"

        const val serializationJson = "org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization"
        const val serializationProtobuf = "org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serialization"
    }

    object AndroidX {

        const val core = "androidx.core:core-ktx:1.12.0"
        const val appCompat = "androidx.appcompat:appcompat:1.6.1"
        const val preference = "androidx.preference:preference-ktx:1.2.1"
        const val workRuntimeKtx = "androidx.work:work-runtime-ktx:2.8.1"
    }

    object Google {
        object PlayServices {

            const val measurementApi = "com.google.android.gms:play-services-measurement-api:21.3.0"
        }
    }

    object Dagger {

        private const val version = "2.48.1"
        const val dagger = "com.google.dagger:dagger:$version"
        const val android = "com.google.dagger:dagger-android:$version"
        const val androidProcessor = "com.google.dagger:dagger-android-processor:$version"
        const val androidSupport = "com.google.dagger:dagger-android-support:$version"
        const val compiler = "com.google.dagger:dagger-compiler:$version"
    }

    object Rx {

        const val rxDogTag = "com.uber.rxdogtag2:rxdogtag:2.0.2"
        const val rxJava = "io.reactivex.rxjava3:rxjava:3.1.7"
        const val rxKotlin = "io.reactivex.rxjava3:rxkotlin:3.0.1"
        const val rxAndroid = "io.reactivex.rxjava3:rxandroid:3.0.2"
    }

    object Apache {

        const val commonsLang3 = "org.apache.commons:commons-lang3:3.13.0"
    }

    object Logging {

        const val slf4jApi = "org.slf4j:slf4j-api:1.7.36" // 2.0.x breaks logging. Code change needed
        const val logbackAndroid = "com.github.tony19:logback-android:2.0.0"
    }

    const val joda = "net.danlew:android.joda:2.12.5"
    const val androidSvg = "com.caverock:androidsvg:1.4"

    const val room = "2.5.2"
    const val lifecycle = "2.6.2"
    const val coroutines = "1.7.3"
    const val activity = "1.8.0"
    const val fragmentktx = "1.6.1"
    const val ormLite = "4.46"
    const val gson = "2.10.1"
    const val nav = "2.7.4"
    const val material = "1.10.0"
    const val gridlayout = "1.0.0"
    const val constraintlayout = "2.1.4"
    const val preferencektx = "1.2.1"
    const val commonslang3 = "3.13.0"
    const val commonscodec = "1.16.0"
    const val guava = "32.1.3-jre"
    const val work = "2.8.1"
    const val tink = "1.10.0"
    const val json = "20230618"
    const val swipe = "1.1.0"

    const val junit = "4.13.2"
    const val junit_jupiter = "5.10.0"
    const val mockito = "5.6.0"
    const val dexmaker = "1.2"
    const val retrofit2 = "2.9.0"
    const val okhttp3 = "4.11.0"
    const val byteBuddy = "1.12.8"

    const val androidx_junit = "1.1.5"
    const val androidx_rules = "1.5.0"

    const val rxandroidble = "1.12.1"
    const val replayshare = "2.2.0"

    const val wearable = "2.9.0"
    const val play_services_wearable = "18.1.0"
    const val play_services_location = "21.0.1"
    const val play_services_measurement = "21.3.0"

    const val kotlinx_datetime = "0.4.1"
    const val kotlinx_serialization = "1.6.0"

    const val caverock_androidsvg = "1.4"
}