package info.nightscout.pump.combov2

import android.util.Log
import info.nightscout.comboctl.base.LogLevel
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.comboctl.base.LoggerBackend as ComboCtlLoggerBackend

internal class AAPSComboCtlLogger(private val aapsLogger: AAPSLogger) : ComboCtlLoggerBackend {
    override fun log(tag: String, level: LogLevel, throwable: Throwable?, message: String?) {
        val ltag = with (tag) {
            when {
                startsWith("Bluetooth") || startsWith("AndroidBluetooth") -> LTag.PUMPBTCOMM
                endsWith("IO") -> LTag.PUMPCOMM
                else -> LTag.PUMP
            }
        }

        val fullMessage = "[$tag]" +
            (if (throwable != null) " (${throwable::class.qualifiedName}: \"${throwable.message}\")" else "") +
            (if (message != null) " $message" else "")

        val stackInfo = Throwable().stackTrace[1]
        val className = stackInfo.className.substringAfterLast(".")
        val methodName = stackInfo.methodName
        val lineNumber = stackInfo.lineNumber

        when (level) {
            // Log verbose content directly with Android's logger to not let this
            // end up in AndroidAPS log files, which otherwise would quickly become
            // very big, since verbose logging produces a lot of material.
            LogLevel.VERBOSE -> Log.v(tag, message, throwable)

            LogLevel.DEBUG   -> aapsLogger.debug(className, methodName, lineNumber, ltag, fullMessage)
            LogLevel.INFO    -> aapsLogger.info(className, methodName, lineNumber, ltag, fullMessage)
            LogLevel.WARN    -> aapsLogger.warn(className, methodName, lineNumber, ltag, fullMessage)
            LogLevel.ERROR   -> aapsLogger.error(className, methodName, lineNumber, ltag, fullMessage)
        }
    }
}
