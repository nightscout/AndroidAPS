package info.nightscout.shared.impl.logging

import info.nightscout.rx.interfaces.L
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import org.slf4j.LoggerFactory

/**
 * Created by adrian on 2019-12-27.
 */

class AAPSLoggerProduction constructor(val l: L) : AAPSLogger {

    override fun debug(message: String) {
        LoggerFactory.getLogger(LTag.CORE.tag).debug(stackLogMarker() + message)
    }

    override fun debug(enable: Boolean, tag: LTag, message: String) {
        if (enable && l.findByName(tag.tag).enabled)
            LoggerFactory.getLogger(tag.tag).debug(stackLogMarker() + message)
    }

    override fun debug(tag: LTag, message: String) {
        if (l.findByName(tag.tag).enabled)
            LoggerFactory.getLogger(tag.tag).debug(stackLogMarker() + message)
    }

    override fun debug(tag: LTag, accessor: () -> String) {
        if (l.findByName(tag.tag).enabled)
            LoggerFactory.getLogger(tag.tag).debug(stackLogMarker() + accessor.invoke())
    }

    override fun debug(tag: LTag, format: String, vararg arguments: Any?) {
        if (l.findByName(tag.tag).enabled)
            LoggerFactory.getLogger(tag.tag).debug(stackLogMarker() + format, arguments)
    }

    override fun warn(tag: LTag, message: String) {
        if (l.findByName(tag.tag).enabled)
            LoggerFactory.getLogger(tag.tag).warn(stackLogMarker() + message)
    }

    override fun warn(tag: LTag, format: String, vararg arguments: Any?) {
        LoggerFactory.getLogger(tag.tag).warn(stackLogMarker() + format, arguments)
    }

    override fun info(tag: LTag, message: String) {
        if (l.findByName(tag.tag).enabled)
            LoggerFactory.getLogger(tag.tag).info(stackLogMarker() + message)
    }

    override fun info(tag: LTag, format: String, vararg arguments: Any?) {
        if (l.findByName(tag.tag).enabled)
            LoggerFactory.getLogger(tag.tag).info(stackLogMarker() + format, arguments)
    }

    override fun error(tag: LTag, message: String) {
        LoggerFactory.getLogger(tag.tag).error(stackLogMarker() + message)
    }

    override fun error(message: String) {
        LoggerFactory.getLogger(LTag.CORE.tag).error(stackLogMarker() + message)
    }

    override fun error(message: String, throwable: Throwable) {
        LoggerFactory.getLogger(LTag.CORE.tag).error(stackLogMarker() + message, throwable)
    }

    override fun error(format: String, vararg arguments: Any?) {
        LoggerFactory.getLogger(LTag.CORE.tag).error(stackLogMarker() + format, arguments)
    }

    override fun error(tag: LTag, message: String, throwable: Throwable) {
        LoggerFactory.getLogger(tag.tag).error(stackLogMarker() + message, throwable)
    }

    override fun error(tag: LTag, format: String, vararg arguments: Any?) {
        LoggerFactory.getLogger(tag.tag).error(stackLogMarker() + format, arguments)
    }

    override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {
        LoggerFactory.getLogger(tag.tag).debug(logLocationPrefix(className, methodName, lineNumber) + message)
    }

    override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {
        LoggerFactory.getLogger(tag.tag).info(logLocationPrefix(className, methodName, lineNumber) + message)
    }

    override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {
        LoggerFactory.getLogger(tag.tag).warn(logLocationPrefix(className, methodName, lineNumber) + message)
    }

    override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {
        LoggerFactory.getLogger(tag.tag).error(logLocationPrefix(className, methodName, lineNumber) + message)
    }
}

private fun logLocationPrefix(className: String, methodName: String, lineNumber: Int) =
    "[$className.$methodName():$lineNumber]: "

fun StackTraceElement.toLogString(): String =
    logLocationPrefix(this.className.substringAfterLast("."), this.methodName, this.lineNumber)

/* Needs to be inline. Don't remove even if IDE suggests it. */
@Suppress("NOTHING_TO_INLINE")
inline fun stackLogMarker() = Throwable().stackTrace[1].toLogString()