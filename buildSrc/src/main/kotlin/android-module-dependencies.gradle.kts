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

    // Gradle Managed Device shared by every library module's androidTest, matching the app module's
    // `emu` device (android-31, google_apis_playstore, x86_64). CI runs <module>:emuFullDebugAndroidTest
    // so AGP owns the emulator lifecycle and merges each run's JaCoCo .ec + JUnit XML through the normal
    // pipeline - see app/build.gradle.kts and .circleci/config.yml.
    testOptions {
        managedDevices {
            localDevices {
                create("emu") {
                    device = "Pixel 6"
                    apiLevel = 31
                    systemImageSource = "google_apis_playstore"
                }
            }
        }
    }
}