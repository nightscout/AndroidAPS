package app.aaps.plugins.eversense.util

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag

/**
 * Bridge from legacy EversenseLogger static calls to AAPS's AAPSLogger.
 *
 * Call [init] once from EversenseCGMPlugin.onStart() to bind the injected
 * AAPSLogger instance.  All existing EversenseLogger.info/debug/error calls
 * then flow through AAPSLogger with LTag.BGSOURCE, matching the rest of AAPS.
 */
class EversenseLogger private constructor() {

    companion object {

        @Volatile
        private var aapsLogger: AAPSLogger? = null

        /** Called once from EversenseCGMPlugin to bind the injected logger. */
        fun init(logger: AAPSLogger) {
            aapsLogger = logger
        }

        fun debug(tag: String, message: String) {
            aapsLogger?.debug(LTag.BGSOURCE, "[$tag] $message")
        }

        fun info(tag: String, message: String) {
            aapsLogger?.info(LTag.BGSOURCE, "[$tag] $message")
        }

        fun warning(tag: String, message: String) {
            aapsLogger?.warn(LTag.BGSOURCE, "[$tag] $message")
        }

        fun error(tag: String, message: String) {
            aapsLogger?.error(LTag.BGSOURCE, "[$tag] $message")
        }
    }
}
