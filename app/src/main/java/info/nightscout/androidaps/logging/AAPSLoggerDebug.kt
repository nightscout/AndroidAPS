package info.nightscout.androidaps.logging

import android.util.Log

/**
 * Created by adrian on 2019-12-27.
 */

class AAPSLoggerDebug : AAPSLogger {

    override fun debug(tag: LTag, message: String) {
        Log.d(tag.tag, message)
    }

    override fun info(tag: LTag, message: String) {
        Log.i(tag.tag, message)
    }

    override fun error(tag: LTag, message: String) {
        Log.e(tag.tag, message)

    }

    override fun error(tag: LTag, message: String, throwable: Throwable) {
        Log.e(tag.tag, message, throwable)

    }
}