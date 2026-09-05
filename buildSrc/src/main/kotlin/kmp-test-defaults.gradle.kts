import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

/**
 * Test task defaults for multiplatform modules.
 *
 * `test-module-dependencies` cannot be used by them - it applies `com.android.library`, which AGP 9
 * refuses alongside the multiplatform plugin - so every module that flipped quietly lost its Test
 * configuration along with its dependencies. This carries just the task settings, applies to any
 * project, and exists so the settings are stated once rather than pasted into seventeen build files.
 *
 * `maxHeapSize` is the one that matters beyond tidiness: without it each forked test JVM defaults to
 * about a quarter of machine RAM, and CI runs the whole unit suite next to three emulators on one
 * self-hosted runner. That pressure is what used to push the emulators offline mid-instrumentation.
 * The fork count is deliberately left alone, for the reasons written up in `test-module-dependencies`.
 */
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    maxHeapSize = "1536m"
    testLogging {
        events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.STANDARD_OUT)
        exceptionFormat = TestExceptionFormat.FULL
    }
}
