plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    // Metro beside Dagger, same as :implementation. CalculationWorkflowImpl and WorkflowChainData are
    // Metro bindings now; the @HiltWorkers here still come from Dagger, so both have to run in this
    // module.
    alias(libs.plugins.metro)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

metro {
    interop {
        // Lets Metro read the javax and Dagger annotations already on this module's classes.
        includeDagger()
    }
}

android {
    namespace = "app.aaps.workflow"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:utils"))

    testImplementation(project(":shared:tests"))

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.hilt.compiler)
}