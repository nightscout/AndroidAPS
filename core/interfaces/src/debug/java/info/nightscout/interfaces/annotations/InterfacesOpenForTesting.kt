package info.nightscout.interfaces.annotations

/**
 * This is the actual annotation that makes the class open. Don't use it directly, only through [InterfacesOpenForTesting]
 * which has a NOOP replacement in production.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class InterfacesOpenClass

/**
 * Annotate a class with [InterfacesOpenForTesting] if it should be extendable for testing.
 */
@InterfacesOpenClass
@Target(AnnotationTarget.CLASS)
annotation class InterfacesOpenForTesting