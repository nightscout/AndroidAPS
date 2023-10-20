plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "info.nightscout.pump.danars"
    ndkVersion = Versions.ndkVersion

    defaultConfig {
        ndk {
            moduleName = "BleCommandUtil"
        }
    }

    sourceSets.getByName("main") {
        jniLibs.srcDirs("src/main/jniLibs")
    }
}

dependencies {
    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))
    implementation(project(":pump:dana"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":core:main"))

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}