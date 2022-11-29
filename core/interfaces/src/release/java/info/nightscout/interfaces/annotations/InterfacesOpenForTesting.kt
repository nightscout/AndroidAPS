package info.nightscout.interfaces.annotations

/**
 * Annotate a class with InterfacesOpenForTestingif it should be extendable for testing.
 * In production the class remains final.
 */
@Target(AnnotationTarget.CLASS)
annotation class InterfacesOpenForTesting