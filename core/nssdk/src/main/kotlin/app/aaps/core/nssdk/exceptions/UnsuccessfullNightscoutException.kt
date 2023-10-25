package app.aaps.core.nssdk.exceptions

class UnsuccessfullNightscoutException : NightscoutException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}
