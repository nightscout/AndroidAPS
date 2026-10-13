import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("com.android.library")
    id("kotlin-android")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementationFromCatalog("org-junit-jupiter")
    testImplementationFromCatalog("org-junit-jupiter-api")
    testImplementationFromCatalog("org-mockito-junit-jupiter")
    testImplementationFromCatalog("org-mockito-kotlin")
    testImplementationFromCatalog("joda-time")
    testImplementationFromCatalog("com-google-truth")
    testImplementationFromCatalog("org-skyscreamer-jsonassert")

    androidTestImplementationFromCatalog("androidx-espresso-core")
    androidTestImplementationFromCatalog("androidx-test-ext")
    androidTestImplementationFromCatalog("androidx-test-rules")
    androidTestImplementationFromCatalog("com-google-truth")
    androidTestImplementationFromCatalog("androidx-uiautomator")
    androidTestImplementationFromCatalog("org-mockito-android")
    androidTestImplementationFromCatalog("org-mockito-kotlin")
}

tasks.withType<Test> {
    // use to display stdout in travis
    testLogging {
        // set options for log level LIFECYCLE
        events = setOf(
            TestLogEvent.FAILED,
            TestLogEvent.STARTED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT
        )
        exceptionFormat = TestExceptionFormat.FULL
        useJUnitPlatform()
    }
}

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

android {
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/COPYRIGHT"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
}
