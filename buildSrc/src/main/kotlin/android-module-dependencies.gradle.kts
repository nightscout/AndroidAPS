plugins {
    id("com.android.library")
}

android {
    compileSdk = Versions.compileSdk
    defaultConfig {
        minSdk = Versions.minSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))
        }
        named("debug") {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }

    sourceSets {
        named("main") {
            jniLibs.directories.add("src/main/jniLibs")
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

    flavorDimensions.add("standard")
    productFlavors {
        create("full") {
            isDefault = true
            dimension = "standard"
        }
        create("pumpcontrol") {
            dimension = "standard"
        }
        create("aapsclient") {
            dimension = "standard"
        }
        create("aapsclient2") {
            dimension = "standard"
        }
        create("aapsclient3") {
            dimension = "standard"
        }
    }

    buildFeatures {
        // disable for modules here
        buildConfig = false
        viewBinding = true
    }
}