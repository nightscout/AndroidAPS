package app.aaps.plugins.dexcomoneplus

internal actual fun onePlusSleep(millis: Long): Boolean =
    try {
        Thread.sleep(millis)
        true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }
