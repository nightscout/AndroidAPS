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

    namespace = "app.aaps.pump.equil"
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
    implementation(project(":core:validators"))
    implementation(project(":core:keys"))

    testImplementation(project(":shared:tests"))

    api(Libs.AndroidX.fragment)
    api(Libs.AndroidX.navigationFragment)

    api(Libs.AndroidX.Room.room)
    api(Libs.AndroidX.Room.runtime)
    api(Libs.AndroidX.Room.rxJava3)
    ksp(Libs.AndroidX.Room.compiler)
    ksp(Libs.Dagger.compiler)
    ksp(Libs.Dagger.androidProcessor)

    implementation("com.github.bumptech.glide:glide:4.16.0")
}
