package app.aaps.plugins.dexcomoneplus

/**
 * How bad a driver log line is, so each platform can map it onto its own levels.
 */
internal enum class OnePlusLogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Writes one driver log line on the platform the driver is running on.
 *
 * This is the only part of the driver's logging that is platform specific. Everything else - what
 * is logged, when, and under which marker - is shared code, so the two platforms cannot drift.
 */
internal expect fun onePlusLogWrite(level: OnePlusLogLevel, message: String, cause: Throwable?)

/**
 * Where this driver writes what it is doing.
 *
 * On Android it goes through slf4j rather than `android.util.Log` for one reason: AAPS already
 * writes everything that comes through slf4j to `AndroidAPS.log`, a rolling file with months of
 * history, and **Maintenance, Send logs** already collects those files. A driver that logged
 * straight to Android would be invisible to that: its lines would live only in the phone's own ring
 * buffer, which holds minutes on a busy phone and which an app may not even be allowed to read back.
 *
 * The line still reaches the phone's log as well, under the same tag as before, so
 * `adb logcat | grep DEXCOM_ONEPLUS` keeps working while a developer is watching.
 *
 * Note that this writes whatever it is given, without asking the AAPS log settings. That is on
 * purpose for a CGM driver: the whole point of these lines is to exist
 * when something fails, and a user who has hit a failure cannot go back and turn logging on.
 */
internal object OnePlusLog {

    fun d(message: String) = onePlusLogWrite(OnePlusLogLevel.DEBUG, message, null)

    fun i(message: String) = onePlusLogWrite(OnePlusLogLevel.INFO, message, null)

    fun w(message: String) = onePlusLogWrite(OnePlusLogLevel.WARN, message, null)

    fun e(message: String) = onePlusLogWrite(OnePlusLogLevel.ERROR, message, null)

    fun e(message: String, cause: Throwable) = onePlusLogWrite(OnePlusLogLevel.ERROR, message, cause)
}
