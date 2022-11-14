package info.nightscout.database.annotations

/**
 * Annotate a class with [DbOpenForTesting] if it should be extendable for testing.
 * In production the class remains final.
 */
@Target(AnnotationTarget.CLASS)
annotation class DbOpenForTesting