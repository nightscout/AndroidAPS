package app.aaps.annotations

/**
 * This is the actual annotation that makes the class open. Don't use it directly, only through [OpenForTesting]
 * which has a NOOP replacement in production.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class OpenClass

/**
 * Annotate a class with [OpenForTesting] if it should be extendable for testing.
 */
@OpenClass
@Target(AnnotationTarget.CLASS)
annotation class OpenForTesting