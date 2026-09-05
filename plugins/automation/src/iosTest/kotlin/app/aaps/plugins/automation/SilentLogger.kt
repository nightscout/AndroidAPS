package app.aaps.plugins.automation

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag

/**
 * A logger that says nothing, for tests that need one but do not care what it does.
 *
 * Shared rather than declared again in each test class: this is twenty no-op overrides, and a second
 * copy was already sitting in `IosBluetoothAbsenceTest`.
 */
internal object SilentLogger : AAPSLogger {

    override fun debug(message: String) {}
    override fun debug(enable: Boolean, tag: LTag, message: String) {}
    override fun debug(tag: LTag, message: String) {}
    override fun debug(tag: LTag, accessor: () -> String) {}
    override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
    override fun warn(tag: LTag, message: String) {}
    override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
    override fun info(tag: LTag, message: String) {}
    override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
    override fun error(tag: LTag, message: String) {}
    override fun error(tag: LTag, message: String, throwable: Throwable) {}
    override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
    override fun error(message: String) {}
    override fun error(message: String, throwable: Throwable) {}
    override fun error(format: String, vararg arguments: Any?) {}
    override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
}
