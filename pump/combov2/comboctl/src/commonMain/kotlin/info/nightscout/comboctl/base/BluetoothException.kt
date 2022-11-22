package info.nightscout.comboctl.base

/**
 * Base class for Bluetooth specific exceptions.
 *
 * @param message The detail message.
 * @param cause Throwable that further describes the cause of the exception.
 */
open class BluetoothException(message: String?, cause: Throwable?) : ComboIOException(message, cause) {
    constructor(message: String) : this(message, null)
    constructor(cause: Throwable) : this(null, cause)
}

/**
 * Base class for exceptions that get thrown when permissions for scanning, connecting etc. are missing.
 *
 * Subclasses contain OS specific information, like the exact missing permissions.
 *
 * @param message The detail message.
 * @param cause Throwable that further describes the cause of the exception.
 */
open class BluetoothPermissionException(message: String?, cause: Throwable?) : BluetoothException(message, cause) {
    constructor(message: String) : this(message, null)
    constructor(cause: Throwable) : this(null, cause)
}
