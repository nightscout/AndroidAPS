plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("com.google.devtools.ksp")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {

    namespace = "info.nightscout.pump.diaconn"
    defaultConfig {
        ksp {
            arg("room.incremental", "true")
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:libraries"))
    implementation(project(":core:objects"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":shared:impl"))

    api(Libs.AndroidX.Room.room)
    api(Libs.AndroidX.Room.runtime)
    api(Libs.AndroidX.Room.rxJava3)
    ksp(Libs.AndroidX.Room.compiler)

    api(Libs.Squareup.Okhttp3.okhttp)
    api(Libs.Squareup.Retrofit2.retrofit)
    api(Libs.Squareup.Retrofit2.converterGson)

    ksp(Libs.Dagger.compiler)
    ksp(Libs.Dagger.androidProcessor)
}