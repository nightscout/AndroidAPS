plugins {
    alias(libs.plugins.android.library)
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}


android {

    namespace = "app.aaps.pump.equil"
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
    implementation(project(":core:libraries"))
    implementation(project(":core:objects"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))
    implementation(project(":core:keys"))

    testImplementation(project(":shared:tests"))

    api(libs.androidx.fragment)
    api(libs.androidx.navigation.fragment)

    api(libs.androidx.room)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.rxjava3)
    kapt(libs.androidx.room.compiler)
    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)

    implementation("com.github.bumptech.glide:glide:4.16.0")
}
