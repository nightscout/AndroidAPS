/**
 * Opens classes marked `@OpenForTesting`, so Mockito can mock them without them being open in the
 * source.
 *
 * Applies only the all-open compiler plugin and nothing else, so a multiplatform module can use it.
 * It used to apply `com.android.library` too, which AGP 9 refuses next to the multiplatform plugin,
 * so no module could apply it and the modules that needed it each wrote the annotation name again.
 */
plugins {
    kotlin("plugin.allopen")
}

allOpen {
    annotation("app.aaps.annotations.OpenForTesting")
}
