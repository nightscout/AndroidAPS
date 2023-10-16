plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    compileSdk = Versions.compileSdk
    defaultConfig {
        multiDexEnabled = true
        versionCode = Versions.versionCode
        version = Versions.appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"))
        }
    }

    compileOptions {
        sourceCompatibility = Versions.javaVersion
        targetCompatibility = Versions.javaVersion
    }

    lint {
        checkReleaseBuilds = false
        disable += "MissingTranslation"
        disable += "ExtraTranslation"
    }
}