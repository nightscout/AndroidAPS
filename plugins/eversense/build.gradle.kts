plugins {
    id("kmp-android-library")
}

kotlin {
    android {
        namespace = "com.nightscout.eversense"
    }
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.core)
        }
    }
}
