plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.omnipod.common"
}

// AGP 8.9+ 库模块 navigation 误报修复:
// 库模块 merge 时 navigation 资源走 navigation_json 机制(不进 merged_res),
// 但 verify*Resources 任务误报 not found → 禁用该检查(不影响产物)
tasks.configureEach {
    if (name.startsWith("verify") && name.endsWith("Resources")) {
        enabled = false
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:libraries"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    api(libs.androidx.constraintlayout)
    api(libs.androidx.fragment)
    api(libs.androidx.navigation.fragment)
    api(libs.com.google.android.material)

    testImplementation(project(":shared:tests"))

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)
}
