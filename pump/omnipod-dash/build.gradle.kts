plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
}

apply(from = "${project.rootDir}/core/main/jacoco_global.gradle")

android {

    namespace = "info.nightscout.androidaps.plugins.pump.omnipod.dash"
    defaultConfig {
        kapt {
            arguments {
                arg("room.incremental", "true")
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }
}

dependencies {
    implementation(project(":database:entities"))
    implementation(project(":database:impl"))
    implementation(project(":core:libraries"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))
    implementation(project(":pump:pump-common"))
    implementation(project(":pump:omnipod-common"))

    api(Libs.AndroidX.Room.room)
    api(Libs.AndroidX.Room.runtime)
    api(Libs.AndroidX.Room.rxJava3)
    api(Libs.kulid)

    androidTestImplementation(project(":shared:tests"))
    testImplementation(project(":shared:tests"))
    testImplementation(Libs.commonCodecs)

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
    kapt(Libs.AndroidX.Room.compiler)
}
