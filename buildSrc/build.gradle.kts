plugins {
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

repositories {
    mavenCentral()
    maven("https://dl.google.com/dl/android/maven2/")
    gradlePluginPortal()
}

dependencies {
    implementation(libs.com.android.tools.build)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.allopen)
}