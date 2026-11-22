plugins {
    id("kmp-android-library")
    id("kotlinx-serialization")
    // @OpenForTesting: the session tests replace the 365 crypto with a fake, also on iOS
    id("all-open-dependencies")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.metro)
}

// The pairing wizard and the other shared screens are in commonMain and use the generated EversenseStrings
registerKeyStrings("eversense")

// The whole Eversense CGM in one module, as a pump driver has one: the transmitter driver and the AAPS plugin with its
// screens. :plugins:source does not depend on it; it depends on :plugins:source for the shared BG source base.
kotlin {
    android {
        namespace = "app.aaps.plugins.eversense"
        androidResources { enable = true }
        withHostTest { isIncludeAndroidResources = true; isReturnDefaultValues = true }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:data"))
            implementation(project(":core:interfaces"))
            implementation(project(":core:keys"))
            implementation(project(":core:objects"))
            implementation(project(":core:ui"))
            implementation(project(":core:utils"))
            implementation(project(":plugins:source"))

            api(project.dependencies.platform(libs.kotlinx.serialization.bom))
            api(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            // P-256 keys, ECDSA, ECDH, HMAC and the AES block of the 365 session: JCA on Android, CryptoKit and
            // CommonCrypto on iOS
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
            // The Eversense cloud (DMS): sign-in, registration of a 365 and the upload
            implementation(libs.io.ktor.client.core)
            api(libs.jetbrains.compose.runtime)
            api(libs.jetbrains.compose.foundation)
            api(libs.jetbrains.compose.ui)
            api(libs.jetbrains.compose.material3)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            api(libs.jetbrains.lifecycle.runtime.compose)
        }
        androidMain.dependencies {
            api(libs.androidx.core)
            implementation(libs.io.ktor.client.okhttp)
            implementation(libs.androidx.appcompat)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.compose.runtime)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
        jvmMain.dependencies {
            implementation(libs.io.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.io.ktor.client.darwin)
        }
        // Tests that run on every target, iOS too: kotlin.test and small fakes, no Truth, Mockito or JVM classes
        commonTest.dependencies {
            implementation(project(":shared:tests"))
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // Hand written: the test convention plugins apply com.android.library, which a multiplatform module cannot.
        getByName("androidHostTest").dependencies {
            implementation(project(":shared:tests"))
            implementation(kotlin("test"))
            implementation(libs.org.junit.jupiter)
            implementation(libs.org.junit.jupiter.api)
            implementation(libs.org.mockito.kotlin)
            implementation(libs.org.mockito.junit.jupiter)
            implementation(libs.com.google.truth)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.org.robolectric)
            // The real org.json: the platform stub answers null under isReturnDefaultValues
            implementation(libs.org.json.android)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
            implementation(libs.com.squareup.okhttp3.mockwebserver)
            runtimeOnly(libs.org.junit.vintage.engine)
            runtimeOnly(libs.org.junit.platform.launcher)
        }
    }
}
