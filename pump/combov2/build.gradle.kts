plugins {
    alias(libs.plugins.android.library)
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
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:libraries"))
    implementation(project(":core:objects"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))
    implementation(project(":core:validators"))
    implementation(project(":pump:combov2:comboctl"))

    api(libs.androidx.lifecycle.viewmodel)
    api(libs.kotlinx.datetime)

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
    api(platform(libs.kotlinx.serialization.bom))
    runtimeOnly(libs.kotlinx.serialization.core)

    kapt(libs.com.google.dagger.compiler)
    kapt(libs.com.google.dagger.android.processor)
}