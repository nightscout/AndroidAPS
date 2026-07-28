import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("com.android.library")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementationFromCatalog("org-junit-jupiter")
    testImplementationFromCatalog("org-junit-jupiter-api")
    testRuntimeOnlyFromCatalog("org-junit-platform-launcher")
    testImplementationFromCatalog("org-mockito-junit-jupiter")
    testImplementationFromCatalog("org-mockito-kotlin")
    testImplementationFromCatalog("joda-time")
    testImplementationFromCatalog("com-google-truth")
    testImplementationFromCatalog("org-skyscreamer-jsonassert")
    testImplementationFromCatalog("kotlinx-coroutines-test")

    androidTestImplementationFromCatalog("androidx-test-ext")
    androidTestImplementationFromCatalog("androidx-test-rules")
    androidTestImplementationFromCatalog("com-google-truth")
    androidTestImplementationFromCatalog("org-mockito-android")
    androidTestImplementationFromCatalog("org-mockito-kotlin")
    androidTestImplementationFromCatalog("kotlinx-coroutines-test")
}

tasks.withType<Test> {
    // use to display stdout in travis
    testLogging {
        // set options for log level LIFECYCLE
        events = setOf(
            TestLogEvent.FAILED,
            //TestLogEvent.STARTED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT
        )
        exceptionFormat = TestExceptionFormat.FULL
        useJUnitPlatform()
    }
}

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
    // CI runs the unit suite alongside three emulators + instrumentation shards on one self-hosted runner.
    // Bound BOTH the fork count and each fork's heap so unit tests can't oversubscribe the box: without an
    // explicit maxHeapSize every forked test JVM defaults its max heap to ~25% of machine RAM, which stacked
    // on the 8g Gradle daemon + 2g Kotlin daemon + three emulators pushed the runner into OOM-kill / ANR
    // territory. 1536m is ample for these mock/coroutine-test unit tests.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1).coerceAtMost(4)
    maxHeapSize = "1536m"
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
