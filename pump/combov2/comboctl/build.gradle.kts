plugins {
    id("com.android.library")
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

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

    api(Libs.KotlinX.coroutinesCore)
    api(Libs.KotlinX.datetime)
    api(Libs.AndroidX.core)

    testImplementation(kotlin("test"))
    testImplementation(Libs.kotlinTestRunner)
    testRuntimeOnly(Libs.JUnit.jupiterEngine)
}