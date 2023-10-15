
plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-allopen")
}

allOpen {
    // allows mocking for classes w/o directly opening them for release builds
    annotation("app.aaps.annotations.OpenForTesting")
}
