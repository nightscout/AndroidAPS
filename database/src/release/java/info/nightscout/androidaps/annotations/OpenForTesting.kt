package info.nightscout.androidaps.annotations

/**
 * Annotate a class with [OpenForTesting] if it should be extendable for testing.
 * In production the class remains final.
 */
@Target(AnnotationTarget.CLASS)
annotation class OpenForTesting