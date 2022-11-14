package info.nightscout.database.annotations

/**
 * This is the actual annotation that makes the class open. Don't use it directly, only through [DbOpenForTesting]
 * which has a NOOP replacement in production.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class DbOpenClass

/**
 * Annotate a class with [DbOpenForTesting] if it should be extendable for testing.
 */
@DbOpenClass
@Target(AnnotationTarget.CLASS)
annotation class DbOpenForTesting