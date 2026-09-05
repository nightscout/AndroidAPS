package app.aaps.core.interfaces.logging

/**
 * Created by adrian on 2019-12-27.
 */

interface AAPSLogger {

    fun debug(message: String)
    fun debug(enable: Boolean, tag: LTag, message: String)
    fun debug(tag: LTag, message: String)
    fun debug(tag: LTag, accessor: () -> String)
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

    // These are variants of the calls above that allow for explicitly
    // specifying the exact logging location. They are primarily meant
    // as a way to integrate other logging infrastructures into AAPS,
    // and typically aren't practical to use directly for logging in code.
    fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String)
    fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String)
    fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String)
    fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String)
}