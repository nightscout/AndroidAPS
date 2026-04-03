plugins {
    alias(libs.plugins.android.library)
    id("android-module-dependencies")
}


android {

    namespace = "com.jjoe64.graphview"
}

dependencies {
    api(libs.androidx.core)
}