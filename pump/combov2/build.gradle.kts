plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "info.nightscout.pump.combov2"
    buildFeatures {
        dataBinding = true
    }
}

dependencies {
    implementation(project(":core:libraries"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:main"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":pump:combov2:comboctl"))

    api(Libs.AndroidX.lifecycleViewmodel)
    api(Libs.KotlinX.datetime)

    // This is necessary to avoid errors like these which otherwise come up often at runtime:
    // "WARNING: Failed to transform class kotlinx/datetime/TimeZone$Companion
    // java.lang.NoClassDefFoundError: kotlinx/serialization/KSerializer"
    //
    // "Rejecting re-init on(previously-failed class java.lang.Class<
    // kotlinx.datetime.serializers.LocalDateTimeIso8601Serializer>:
    // java.lang.NoClassDefFoundError: Failed resolution of: Lkotlinx/serialization/KSerializer"
    //
    // kotlinx-datetime higher than 0.2.0 depends on kotlinx-serialization, but that dependency
    // is declared as "compileOnly". The runtime dependency on kotlinx-serialization is missing,
    // causing this error. Solution is to add runtimeOnly here.
    //
    // Source: https://github.com/mockk/mockk/issues/685#issuecomment-907076353:
    // TODO: Revisit this when upgrading kotlinx-datetime
    runtimeOnly(Libs.KotlinX.serializationCore)

    kapt(Libs.Dagger.compiler)
    kapt(Libs.Dagger.androidProcessor)
}