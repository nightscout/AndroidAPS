plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    id("kotlinx-serialization")
}

kotlin {
    jvm {
        // Every consumer of :core:nssdk is still an Android module, and they resolve this variant.
        // Nothing about them changes.
        compilerOptions {
            jvmTarget.set(Versions.jvmTarget)
        }
    }

    // Real Apple targets, same as :core:data. They cross compile on Windows; only linking, cinterop
    // and running their tests need a Mac.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(libs.io.ktor.client.core)
                implementation(libs.io.ktor.client.content.negotiation)
                implementation(libs.io.ktor.serialization.kotlinx.json)
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                // The client-control signing and pairing path. Not javax.crypto: a follower on iOS
                // signs the commands it sends, and javax does not exist there. `optimal` picks each
                // platform's own implementation - JCA on the JVM, CryptoKit on Apple, OpenSSL 3 on
                // the other native targets - so no algorithm is reimplemented here.
                implementation(libs.cryptography.core)
                implementation(libs.cryptography.provider.optimal)
            }
        }
        getByName("jvmMain") {
            dependencies {
                // OkHttp is both the Ktor engine and, on the JVM side, what the rest of the app
                // already uses. The client-control crypto in this source set is javax.crypto.
                api(libs.com.squareup.okhttp3.okhttp)
                implementation(libs.io.ktor.client.okhttp)
            }
        }
        // Accessor rather than getByName: iosMain is created by the default hierarchy template,
        // which is applied after this block is evaluated, so getByName("iosMain") fails.
        iosMain {
            dependencies {
                // Darwin runs on NSURLSession, so an iOS build gets the system's own connection
                // handling rather than a second HTTP stack.
                implementation(libs.io.ktor.client.darwin)
            }
        }
        // The golden crypto vectors live here so they run on every target, not just the JVM. That is
        // the whole point of them: they were produced by the old javax implementation, so they prove
        // the Apple and Windows builds still emit the same bytes. kotlin.test only - JUnit and Truth
        // are JVM-only and would keep the vectors off Native.
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        getByName("jvmTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                runtimeOnly(libs.org.junit.platform.launcher)
                implementation(libs.com.google.truth)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.kotlinx.coroutines.test)
                // A real HTTP server on localhost, so the contract tests exercise the whole stack.
                implementation(libs.com.squareup.okhttp3.mockwebserver)
            }
        }
    }
}
