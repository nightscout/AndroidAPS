plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "info.nightscout.androidaps.plugins.pump.medtronic"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:libraries"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))
    implementation(project(":pump:pump-common"))
    implementation(project(":pump:rileylink"))

    testImplementation(project(":core:keys"))
    testImplementation(project(":shared:tests"))

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}
