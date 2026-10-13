plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {

    namespace = "app.aaps.pump.insight"
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
    implementation(project(":core:keys"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))
    implementation(project(":pump:common"))
    testImplementation(project(":shared:tests"))

    api(libs.com.google.android.material)
    api(libs.androidx.room)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.rxjava3)

    ksp(libs.androidx.room.compiler)
    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)
}