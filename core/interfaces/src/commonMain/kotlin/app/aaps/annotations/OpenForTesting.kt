package app.aaps.annotations

/**
 * Annotate a class with [OpenForTesting] if it should be extendable for testing.
 *
 * The allOpen plugin is configured with this annotation's own name, in
 * `all-open-dependencies.gradle.kts`, so the annotation alone is what opens a class.
 *
 * Note this means an annotated class is open in release builds too: the three modules applying
 * allOpen apply it to every variant.
 */
@Target(AnnotationTarget.CLASS)
annotation class OpenForTesting
