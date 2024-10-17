@Suppress("SpellCheckingInspection")
object Libs {

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

        object Wear {

            const val wear = "androidx.wear:wear:1.3.0"
            const val tiles = "androidx.wear.tiles:tiles:1.2.0"
        }

        const val biometric = "androidx.biometric:biometric:1.1.0"
        const val media3 = "androidx.media3:media3-common:1.1.1"
    }

    object Google {

        const val truth = "com.google.truth:truth:1.1.5"
    }

    object Apache {

        const val commonsLang3 = "org.apache.commons:commons-lang3:3.13.0"
    }

    object Logging {

        const val slf4jApi = "org.slf4j:slf4j-api:2.0.7"
        const val logbackAndroid = "com.github.tony19:logback-android:3.0.0"
    }

    object JUnit {

        private const val junitVersion = "5.10.1"

        const val jupiter = "org.junit.jupiter:junit-jupiter:$junitVersion"
        const val jupiterApi = "org.junit.jupiter:junit-jupiter-api:$junitVersion"
        const val jupiterEngine = "org.junit.jupiter:junit-jupiter-engine:$junitVersion"
    }

    object Mockito {
        private const val mockitoVersion = "5.10.0"

        const val android = "org.mockito:mockito-android:$mockitoVersion"
        const val core = "org.mockito:mockito-core:$mockitoVersion"
        const val jupiter = "org.mockito:mockito-junit-jupiter:$mockitoVersion"
        const val kotlin = "org.mockito.kotlin:mockito-kotlin:5.1.0"
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