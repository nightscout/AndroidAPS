package app.aaps.plugins.dexcomoneplus

import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger(OnePlusLogMarkers.TAG)

/**
 * Android sink: slf4j, which AAPS already copies into `AndroidAPS.log` and into logcat under the
 * same tag as before. See `OnePlusLog` for why this driver does not log straight to Android.
 */
internal actual fun onePlusLogWrite(level: OnePlusLogLevel, message: String, cause: Throwable?) {
    when (level) {
        OnePlusLogLevel.DEBUG -> logger.debug(message)
        OnePlusLogLevel.INFO  -> logger.info(message)
        OnePlusLogLevel.WARN  -> logger.warn(message)
        OnePlusLogLevel.ERROR -> if (cause == null) logger.error(message) else logger.error(message, cause)
    }
}
