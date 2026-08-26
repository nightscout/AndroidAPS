plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // Metro beside Hilt in the same module, so one feature can move while the rest stays on Hilt.
    // This is what makes the migration incremental.
    alias(libs.plugins.metro)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("compose-test-module-dependencies")
    id("jacoco-module-dependencies")
}

metro {
    interop {
        // Teach Metro to read javax.inject and Dagger annotations, so a class does not have to be
        // rewritten to
        // Metro's own @Inject/@Qualifier just to be built by a Metro graph. Without this, Metro
        // ignores javax qualifiers entirely - five differently qualified Strings here collapsed into
        // one binding, and the graph failed to compile.
        includeDagger()
    }
}

android {
    namespace = "app.aaps.plugins.sync"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":shared:impl"))

    // Compose
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)


    implementation(libs.kotlinx.datetime)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)

    testImplementation(project(":shared:tests"))
    testImplementation(project(":implementation"))
    testImplementation(project(":plugins:aps"))
    androidTestImplementation(project(":shared:tests"))

    // OpenHuman
    api(libs.com.squareup.okhttp3.okhttp)
    api(libs.com.squareup.retrofit2.retrofit)
    implementation(libs.androidx.browser)

    // NSClient, Tidepool
    api(libs.io.socket.client)
    implementation(libs.com.squareup.okhttp3.logging.interceptor)
    implementation(libs.com.squareup.retrofit2.converter.gson)
    api(libs.com.google.code.gson)
    api(libs.net.openid.appauth)

    // DataLayerListenerService
    api(libs.com.google.android.gms.playservices.wearable)

    // SMS Communicator (OTP + QR code)
    implementation(libs.com.eatthepath.java.otp)
    implementation(libs.com.github.kenglxn.qrgen.android)
    // ZXing is pulled transitively by qrgen but SmsCommunicatorOtpScreen imports ErrorCorrectionLevel
    // directly — declare it explicitly so a future qrgen upgrade can't silently drop the symbol.
    implementation(libs.com.google.zxing.core)

    // Garmin
    api(libs.com.garmin.connectiq) { artifact { type = "aar" } }
    androidTestImplementation(libs.com.garmin.connectiq) { artifact { type = "aar" } }

    implementation(libs.com.google.dagger.hilt.android)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.hilt.compiler)
    ksp(libs.com.google.dagger.android.processor)
}