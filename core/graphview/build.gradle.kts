plugins {
    id("com.android.library")
    id("kotlin-android")
    id("android-module-dependencies")
}


android {

    namespace = "com.jjoe64.graphview"
}

dependencies {
    api(Libs.AndroidX.core)
}