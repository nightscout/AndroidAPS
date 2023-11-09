package app.aaps.plugins.aps.logger

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.ScriptLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.plugins.aps.utils.StaticInjector
import org.mozilla.javascript.ScriptableObject
import javax.inject.Inject

@Suppress("unused", "FunctionName")
class LoggerCallback @Inject internal constructor(): ScriptableObject(

), ScriptLogger {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var profileUtil: ProfileUtil

    override fun getClassName(): String = "LoggerCallback"

    fun jsConstructor() {
        //empty constructor on JS site; could work as setter
    }

    fun jsFunction_log(obj1: Any) {
        debug(obj1.toString())
    }

    fun jsFunction_error(obj1: Any) {
        error(obj1.toString())
    }

    fun jsFunction_header(obj1: Any) {
        header(obj1.toString())
    }

    override fun debug(message: String) {
        aapsLogger.debug(LTag.APS, message)
        ScriptLogger.Companion.debug(message)
    }

    override fun debugUnits(message: String, value: Double) {
        debug(message, profileUtil.valueInCurrentUnitsDetect(value))
    }

    override fun debug(message: String, vararg values : Any) {
        debug(String.format(message, *values))
                }

    override fun error(message: String) {
        aapsLogger.error(LTag.APS, message)
        ScriptLogger.Companion.error(message)
                }

    override fun header(message: String) {
        aapsLogger.debug(LTag.APS, message)
        footer()
        ScriptLogger.Companion.debug("     $message")
        footer()
            }

    override fun footer() {
        ScriptLogger.Companion.debug("---------------------------------------------------------")
    }

    init {
        @Suppress("DEPRECATION")
        StaticInjector.getInstance().androidInjector().inject(this)
    }
}