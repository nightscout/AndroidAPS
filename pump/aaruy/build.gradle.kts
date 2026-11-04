plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.aaruy"
    buildFeatures {
        dataBinding = true
    }
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
    implementation(project(":core:libraries"))
    implementation(project(":core:objects"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))
    implementation(project(":pump:common"))
    implementation(project(":shared:impl"))
    implementation(project(":pump:aaruy:protocol"))
    implementation(project(":pump:aaruy:emulator"))

    implementation("no.nordicsemi.android.support.v18:scanner:1.6.0")
    implementation(libs.com.google.code.gson)
    testImplementation(files("protocol/libs/mylibrary-release.aar"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":core:objects"))
    testImplementation("org.robolectric:robolectric:4.17")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:6.0.1")

    api(libs.androidx.room)
    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)
}
