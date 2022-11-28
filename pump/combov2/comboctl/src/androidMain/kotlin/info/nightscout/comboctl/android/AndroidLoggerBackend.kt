package info.nightscout.comboctl.android

import android.util.Log
import info.nightscout.comboctl.base.LogLevel
import info.nightscout.comboctl.base.LoggerBackend

class AndroidLoggerBackend : LoggerBackend {
    override fun log(tag: String, level: LogLevel, throwable: Throwable?, message: String?) {
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, message, throwable)
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
    }
}
