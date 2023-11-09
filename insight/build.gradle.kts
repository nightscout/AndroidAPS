plugins {
    id("com.android.library")
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

    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":pump:pump-common"))

    api(Libs.Google.Android.material)
    api(Libs.AndroidX.Room.room)
    api(Libs.AndroidX.Room.runtime)
    api(Libs.AndroidX.Room.rxJava3)

    kapt(Libs.AndroidX.Room.compiler)
    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}