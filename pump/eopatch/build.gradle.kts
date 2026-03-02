plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.eopatch"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:libraries"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":pump:eopatch:core"))
    implementation(project(":core:validators"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":core:objects"))

    api(libs.com.google.guava)

    //RxAndroidBle
    api(libs.io.reactivex.rxjava3.rxandroid)
    api(libs.com.polidea.rxandroidble3)
    api(libs.com.jakewharton.rx3.replaying.share)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)
}
