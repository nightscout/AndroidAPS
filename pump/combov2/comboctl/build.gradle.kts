plugins {
    id("com.android.library")
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
}

apply(from = "${project.rootDir}/core/main/jacoco_global.gradle")

android {
    namespace = "info.nightscout.comboctl"
    sourceSets.getByName("main") {
        kotlin.srcDir("src/commonMain/kotlin")
        kotlin.srcDir("src/androidMain/kotlin")
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
    sourceSets.getByName("test") {
        kotlin.srcDir("src/jvmTest/kotlin")
    }
}

dependencies {
    implementation(platform(Libs.Kotlin.platformBom))
    testImplementation(Libs.Kotlin.test)
    testImplementation(Libs.Kotlin.testJunit5)


    api(Libs.KotlinX.coroutinesCore)
    api(Libs.KotlinX.datetime)
    api(Libs.AndroidX.core)
    testImplementation(Libs.kotlinTestRunner)
    testRuntimeOnly(Libs.JUnit.jupiterEngine)
}