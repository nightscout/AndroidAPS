plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.metro)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

metro {
    interop {
        // The classes here keep their javax annotations; interop is what lets Metro read them.
        includeDagger()
    }
}

android {
    namespace = "app.aaps.pump.eopatch"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:ui"))

    testImplementation(project(":shared:tests"))
    testImplementation(project(":core:objects"))

    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.com.google.code.gson)

    implementation(libs.com.google.guava)

    //RxAndroidBle
    implementation(libs.io.reactivex.rxjava3.rxandroid)
    api(libs.com.polidea.rxandroidble3)
    implementation(libs.com.jakewharton.rx3.replaying.share)


}
