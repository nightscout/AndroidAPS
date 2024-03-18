@Suppress("SpellCheckingInspection")
object Libs {

    object Kotlin {

        const val kotlin = "1.9.10"

        const val stdlibJdk8 = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin"
        const val reflect = "org.jetbrains.kotlin:kotlin-reflect:$kotlin"
    }

    object KotlinX {

        private const val serialization = "1.6.0"
        private const val coroutinesVersion = "1.7.3"

        const val serializationJson = "org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization"
        const val serializationProtobuf = "org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serialization"
        const val serializationCore = "org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization"
        const val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion"
        const val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion"
        const val coroutinesRx3 = "org.jetbrains.kotlinx:kotlinx-coroutines-rx3:$coroutinesVersion"
        const val coroutinesGuava = "org.jetbrains.kotlinx:kotlinx-coroutines-guava:$coroutinesVersion"
        const val coroutinesPlayServices = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$coroutinesVersion"
        const val coroutinesTest = "org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion"
        const val datetime = "org.jetbrains.kotlinx:kotlinx-datetime:0.4.1"
    }

    object AndroidX {

        const val core = "androidx.core:core-ktx:1.12.0"
        const val appCompat = "androidx.appcompat:appcompat:1.6.1"
        const val activity = "androidx.activity:activity-ktx:1.8.0"
        const val preference = "androidx.preference:preference-ktx:1.2.1"
        const val constraintLayout = "androidx.constraintlayout:constraintlayout:2.1.4"
        const val swipeRefreshLayout = "androidx.swiperefreshlayout:swiperefreshlayout:1.1.0"
        const val gridLayout = "androidx.gridlayout:gridlayout:1.0.0"
        const val browser = "androidx.browser:browser:1.6.0"
        const val lifecycleViewmodel = "androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2"
        const val fragment = "androidx.fragment:fragment-ktx:1.6.1"
        const val navigationFragment = "androidx.navigation:navigation-fragment-ktx:2.7.4"
        const val legacySupport = "androidx.legacy:legacy-support-v13:1.0.0"

        object Work {

            private const val workVersion = "2.8.1"
            const val runtimeKtx = "androidx.work:work-runtime-ktx:$workVersion"
            const val testing = "androidx.work:work-testing:$workVersion"
        }

        object Test {

            const val espressoCore = "androidx.test.espresso:espresso-core:3.5.1"
            const val extKtx = "androidx.test.ext:junit-ktx:1.1.5"
            const val rules = "androidx.test:rules:1.5.0"
            const val uiAutomator = "androidx.test.uiautomator:uiautomator:2.2.0"
        }

        object Room {

            private const val roomVersion = "2.5.2"

            const val room = "androidx.room:room-ktx:$roomVersion"
            const val compiler = "androidx.room:room-compiler:$roomVersion"
            const val runtime = "androidx.room:room-runtime:$roomVersion"
            const val rxJava3 = "androidx.room:room-rxjava3:$roomVersion"
            const val testing = "androidx.room:room-testing:$roomVersion"
        }

        object Wear {

            const val wear = "androidx.wear:wear:1.3.0"
            const val tiles = "androidx.wear.tiles:tiles:1.2.0"
        }

        const val biometric = "androidx.biometric:biometric:1.1.0"
        const val media3 = "androidx.media3:media3-common:1.1.1"
    }

    object Google {

        object Android {
            object PlayServices {

                const val measurementApi = "com.google.android.gms:play-services-measurement-api:21.4.0"
                const val wearable = "com.google.android.gms:play-services-wearable:18.1.0"
                const val location = "com.google.android.gms:play-services-location:21.0.1"
            }

            object Wearable {

                const val wearable = "com.google.android.wearable:wearable:2.9.0"
                const val wearableSupport = "com.google.android.support:wearable:2.9.0"
            }

            const val material = "com.google.android.material:material:1.10.0"
            const val flexbox = "com.google.android.flexbox:flexbox:3.0.0"
        }

        object Firebase {

            const val firebaseBom = "com.google.firebase:firebase-bom:32.4.0"
            const val analytics = "com.google.firebase:firebase-analytics-ktx"
            const val crashlytics = "com.google.firebase:firebase-crashlytics-ktx"
            const val messaging = "com.google.firebase:firebase-messaging-ktx"
            const val auth = "com.google.firebase:firebase-auth-ktx"
            const val database = "com.google.firebase:firebase-database-ktx"
        }

        const val truth = "com.google.truth:truth:1.1.5"
        const val gson = "com.google.code.gson:gson:2.10.1"
        const val guava = "com.google.guava:guava:32.1.3-jre"
        const val tinkAndroid = "com.google.crypto.tink:tink-android:1.10.0"
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

    object JUnit {

        private const val junitVersion = "5.10.0"

        const val jupiter = "org.junit.jupiter:junit-jupiter:$junitVersion"
        const val jupiterApi = "org.junit.jupiter:junit-jupiter-api:$junitVersion"
        const val jupiterEngine = "org.junit.jupiter:junit-jupiter-engine:$junitVersion"
    }

    object Mockito {
        private const val mockitoVersion = "5.6.0"

        const val android = "org.mockito:mockito-android:$mockitoVersion"
        const val core = "org.mockito:mockito-core:$mockitoVersion"
        const val jupiter = "org.mockito:mockito-junit-jupiter:$mockitoVersion"
        const val kotlin = "org.mockito.kotlin:mockito-kotlin:5.1.0"
    }

    object Squareup {
        object Retrofit2 {

            private const val retrofitVersion = "2.9.0"

            const val retrofit = "com.squareup.retrofit2:retrofit:$retrofitVersion"
            const val adapterRxJava3 = "com.squareup.retrofit2:adapter-rxjava3:$retrofitVersion"
            const val converterGson = "com.squareup.retrofit2:converter-gson:$retrofitVersion"
        }

        object Okhttp3 {

            private const val okhttpVersion = "4.12.0"

            const val okhttp = "com.squareup.okhttp3:okhttp:$okhttpVersion"
            const val loggingInterceptor = "com.squareup.okhttp3:logging-interceptor:$okhttpVersion"
        }
    }

    object Mozilla {

        const val rhino = "org.mozilla:rhino:1.7.14"
    }

    const val spongycastleCore = "com.madgag.spongycastle:core:1.58.0.0"
    const val androidSvg = "com.caverock:androidsvg:1.4"
    const val jodaTimeAndroid = "net.danlew:android.joda:2.12.5"
    const val jodaTime = "joda-time:joda-time:2.12.5"
    const val json = "org.json:json:20230618"
    const val jsonAssert = "org.skyscreamer:jsonassert:1.5.0"
    const val rootBeer = "com.scottyab:rootbeer-lib:0.1.0"
    const val javaOtp = "com.eatthepath:java-otp:0.4.0"
    const val qrGen = "com.github.kenglxn.QRGen:android:3.0.1"
    const val socketIo = "io.socket:socket.io-client:2.1.0"
    const val kotlinTestRunner = "io.kotlintest:kotlintest-runner-junit5:3.4.2"
    const val rxandroidBle = "com.polidea.rxandroidble3:rxandroidble:1.17.2"
    const val rx3ReplayingShare = "com.jakewharton.rx3:replaying-share:3.0.0"
    const val commonCodecs = "commons-codec:commons-codec:1.16.0"
    const val kulid = "com.github.guepardoapps:kulid:2.0.0.0"
    const val xstream = "com.thoughtworks.xstream:xstream:1.4.20"
    const val connectiqSdk = "com.garmin.connectiq:ciq-companion-app-sdk:2.0.3@aar"
}