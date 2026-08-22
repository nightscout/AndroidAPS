package app.aaps.annotations

/**
 * Annotate a class with [OpenForTesting] if it should be extendable for testing.
 *
 * There used to be two declarations of this, one per build variant, the debug one carrying an extra
 * `@OpenClass` meta-annotation. Nothing ever read it: the allOpen plugin is configured with this
 * annotation's own name (`all-open-dependencies.gradle.kts`), not with the meta-annotation, so the two
 * declarations behaved identically and the variant split did nothing. One declaration now, and it
 * holds no Android types, so it lives in common code.
 *
 * Note this means an annotated class is open in release builds too. That was already the case - the
 * three modules applying allOpen apply it to every variant - it was just not visible while there were
 * two files suggesting otherwise.
 */
@Target(AnnotationTarget.CLASS)
annotation class OpenForTesting