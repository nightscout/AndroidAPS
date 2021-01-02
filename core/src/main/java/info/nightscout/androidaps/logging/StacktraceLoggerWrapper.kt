package info.nightscout.androidaps.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Created by adrian on 2020-01-13.
 */

class StacktraceLoggerWrapper(private val delegate: Logger) : Logger by delegate {

    override fun debug(msg: String?) {
        delegate.debug(stackLogMarker() + msg)
    }

    override fun debug(format: String?, arg: Any?) {
        delegate.debug(stackLogMarker() + format, arg)
    }

    override fun info(msg: String?) {
        delegate.info(stackLogMarker() + msg)
    }

    override fun error(msg: String?) {
        delegate.error(stackLogMarker() + msg)
    }

    override fun warn(msg: String?) {
        delegate.warn(stackLogMarker() + msg)
    }

    // all other methods will be implemented by delegate

    companion object {
        @JvmStatic
        @Deprecated("please inject AAPSLogger")
        fun getLogger(ltag: LTag) = StacktraceLoggerWrapper(LoggerFactory.getLogger(ltag.name))

        @JvmStatic
        @Deprecated("please inject AAPSLogger")
        fun getLogger(clazz: Class<*>) = StacktraceLoggerWrapper(LoggerFactory.getLogger(clazz))
    }
}