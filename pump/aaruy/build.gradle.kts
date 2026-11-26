plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.metro)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("compose-test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {

    namespace = "app.aaps.pump.aaruy"
}

ksp {
    arg("room.incremental", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<Test> {
    systemProperty("robolectric.dependency.repo.url", "https://maven.aliyun.com/repository/public")
    // 如果还需要代理，可以加上：
    // systemProperty("robolectric.dependency.proxy.host", "your-proxy-host")
    // systemProperty("robolectric.dependency.proxy.port", "your-proxy-port")
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":pump:common"))
    implementation(project(":shared:impl"))

    implementation("no.nordicsemi.android.support.v18:scanner:1.6.0")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation(project(":database:impl"))
    implementation(files("libs/mylibrary-release.aar"))
    implementation("no.nordicsemi.android:ble-ktx:2.7.2")

    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    api(libs.com.squareup.okhttp3.okhttp)
    api(libs.com.squareup.retrofit2.retrofit)
    implementation(libs.com.squareup.retrofit2.converter.gson)

    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.process)

    testImplementation(project(":shared:tests"))

}