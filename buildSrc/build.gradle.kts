plugins {
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.com.android.tools.build)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.allopen)
}