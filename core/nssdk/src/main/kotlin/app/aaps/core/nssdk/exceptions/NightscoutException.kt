package app.aaps.core.nssdk.exceptions

import java.io.IOException

abstract class NightscoutException(message: String, cause: Throwable? = null) : IOException(message, cause)