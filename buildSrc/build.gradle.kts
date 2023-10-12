object KtsBuildVersions {

    const val gradle = "8.1.2"
    const val kotlin = "1.9.0"
}

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
    implementation("com.android.tools.build:gradle:${KtsBuildVersions.gradle}")
//    implementation("com.android.tools.build:gradle-api:${KtsBuildVersions.gradle}")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${KtsBuildVersions.kotlin}")
}