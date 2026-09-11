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
    // CI runs the unit suite alongside three emulators on one self-hosted runner. Bound each forked test
    // JVM's HEAP so the suite can't oversubscribe MEMORY: without maxHeapSize every fork defaults to ~25%
    // of machine RAM, and that pressure (stacked on the 8g Gradle + 2g Kotlin daemons) knocked emulators
    // offline mid-instrumentation. Do NOT also cut maxParallelForks: CPU is already isolated by taskset core
    // pinning in CI, and fewer forks pack more tests per JVM, which surfaces cross-test coroutine-leak /
    // timing flakes (UncaughtExceptionsBeforeTest) that stay dormant at the default fork count. Cap heap,
    // keep the fork count.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
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
