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

    // The same JaCoCo setting `jacoco-module-dependencies` applies, repeated here because a
    // multiplatform module cannot have that convention: it applies `com.android.library`, which AGP 9
    // refuses next to the multiplatform plugin. Without it a Robolectric test records nothing -
    // Robolectric loads classes through its own sandbox classloader and rewrites their bytecode, so
    // the on-the-fly agent sees classes with no source location and skips them. The tests still run
    // and pass; only the coverage is missing, which is the worst shape for a problem to have.
    // Measured on :core:graph, whose only test is a Robolectric Compose one: its exec file held no
    // app/aaps class at all, and ProfileViewerContent.kt reported 0 of 399 lines.
    extensions.findByType(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class)?.apply {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
    testLogging {
        // See the same block in `test-module-dependencies` for why CI drops the passing tests' stdout.
        events = if (providers.environmentVariable("CI").isPresent)
            setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        else
            setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.STANDARD_OUT)
        exceptionFormat = TestExceptionFormat.FULL
    }
}
