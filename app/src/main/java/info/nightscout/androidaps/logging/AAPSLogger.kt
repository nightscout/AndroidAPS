package info.nightscout.androidaps.logging

/**
 * Created by adrian on 2019-12-27.
 */

interface AAPSLogger {

    fun debug(tag: LTag, message: String)
    fun info(tag: LTag, message: String)
    fun error(tag: LTag, message: String)
    fun error(tag: LTag, message: String, throwable: Throwable)
}