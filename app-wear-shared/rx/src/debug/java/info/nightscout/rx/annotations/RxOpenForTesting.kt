package info.nightscout.rx.annotations

/**
 * This is the actual annotation that makes the class open. Don't use it directly, only through [RxOpenForTesting]
 * which has a NOOP replacement in production.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class RxOpenClass

/**
 * Annotate a class with [RxOpenForTesting] if it should be extendable for testing.
 */
@RxOpenClass
@Target(AnnotationTarget.CLASS)
annotation class RxOpenForTesting