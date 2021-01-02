package info.nightscout.androidaps.logging

/**
 * Created by adrian on 2019-12-27.
 */

interface AAPSLogger {

    fun debug(message: String)
    fun debug(enable: Boolean, tag: LTag, message: String)
    fun debug(tag: LTag, message: String)
    fun debug(tag: LTag, format: String, vararg arguments: Any?)
    fun warn(tag: LTag, message: String)
    fun warn(tag: LTag, format: String, vararg arguments: Any?)
    fun info(tag: LTag, message: String)
    fun info(tag: LTag, format: String, vararg arguments: Any?)
    fun error(tag: LTag, message: String)
    fun error(tag: LTag, message: String, throwable: Throwable)
    fun error(tag: LTag, format: String, vararg arguments: Any?)
    fun error(message: String)
    fun error(message: String, throwable: Throwable)
    fun error(format: String, vararg arguments: Any?)
}