package app.aaps.core.nssdk.exceptions

import java.io.IOException

abstract class NightscoutException : IOException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}
