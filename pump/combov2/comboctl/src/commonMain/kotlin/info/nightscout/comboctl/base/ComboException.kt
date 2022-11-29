package info.nightscout.comboctl.base

/**
 * Base class for ComboCtl specific exceptions.
 *
 * @param message The detail message.
 * @param cause Throwable that further describes the cause of the exception.
 */
open class ComboException(message: String?, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String) : this(message, null)
    constructor(cause: Throwable) : this(null, cause)
}
