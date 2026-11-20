package app.aaps.plugins.libre3

import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger(Libre3LogMarkers.TAG)

/**
 * Android sink: slf4j, which AAPS already copies into `AndroidAPS.log` and into logcat under the
 * same tag as before. See `Libre3Log` for why this driver does not log straight to Android.
 */
internal actual fun libre3LogWrite(level: Libre3LogLevel, message: String, cause: Throwable?) {
    when (level) {
        Libre3LogLevel.INFO  -> logger.info(message)
        Libre3LogLevel.WARN  -> logger.warn(message)
        Libre3LogLevel.ERROR -> if (cause == null) logger.error(message) else logger.error(message, cause)
    }
}
