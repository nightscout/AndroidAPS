plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {

    namespace = "info.nightscout.androidaps.insight"
    defaultConfig {
        kapt {
            arguments {
                arg("room.incremental", "true")
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }
}

dependencies {

    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":pump:pump-common"))
    testImplementation(project(":shared:tests"))

    api(libs.com.google.android.material)
    api(libs.androidx.room)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.rxjava3)

    kapt(libs.androidx.room.compiler)
    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}