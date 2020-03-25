package info.nightscout.androidaps.logging

import android.util.Log

/**
 * Created by adrian on 2019-12-27.
 */

class AAPSLoggerDebug : AAPSLogger {

    override fun debug(message: String) {
        Log.d(LTag.CORE.tag, message)
    }

    override fun debug(enable: Boolean, tag: LTag, message: String) {
        if (enable) Log.d(LTag.CORE.tag, message)
    }

    override fun debug(tag: LTag, message: String) {
        Log.d(tag.tag, message)
    }

    override fun debug(tag: LTag, format: String, vararg arguments: Any?) {
        Log.d(tag.tag, String.format(format, arguments))
    }

    override fun warn(tag: LTag, message: String) {
        Log.w(tag.tag, message)
    }

    override fun info(tag: LTag, message: String) {
        Log.i(tag.tag, message)
    }

    override fun info(tag: LTag, format: String, vararg arguments: Any?) {
        Log.i(tag.tag, String.format(format, arguments))
    }

    override fun error(tag: LTag, message: String) {
        Log.e(tag.tag, message)

    }

    override fun error(message: String) {
        Log.e(LTag.CORE.tag, message)
    }

    override fun error(message: String, throwable: Throwable) {
        Log.e(LTag.CORE.tag, message, throwable)
    }

    override fun error(format: String, vararg arguments: Any?) {
        Log.e(LTag.CORE.tag, String.format(format, arguments))
    }

    override fun error(tag: LTag, message: String, throwable: Throwable) {
        Log.e(tag.tag, message, throwable)

    }

    override fun error(tag: LTag, format: String, vararg arguments: Any?) {
        Log.e(tag.tag, String.format(format, arguments))
    }
}