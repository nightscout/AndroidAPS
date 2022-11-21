package info.nightscout.plugins.aps.logger

import info.nightscout.plugins.aps.utils.StaticInjector
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import org.mozilla.javascript.ScriptableObject
import javax.inject.Inject

@Suppress("unused", "FunctionName")
class LoggerCallback : ScriptableObject() {

    @Inject lateinit var aapsLogger: AAPSLogger

    override fun getClassName(): String = "LoggerCallback"

    fun jsConstructor() {
        //empty constructor on JS site; could work as setter
    }

    fun jsFunction_log(obj1: Any) {
        aapsLogger.debug(LTag.APS, obj1.toString().trim { it <= ' ' })
        logBuffer.append(obj1.toString())
    }

    fun jsFunction_error(obj1: Any) {
        aapsLogger.error(LTag.APS, obj1.toString().trim { it <= ' ' })
        errorBuffer.append(obj1.toString())
    }

    companion object {

        private var errorBuffer = StringBuffer()
        private var logBuffer = StringBuffer()
        val scriptDebug: String
            get() {
                var ret = ""
                if (errorBuffer.isNotEmpty()) {
                    ret += """
                    e:
                    $errorBuffer
                    """.trimIndent()
                }
                if (ret.isNotEmpty() && logBuffer.isNotEmpty()) ret += '\n'
                if (logBuffer.isNotEmpty()) {
                    ret += """
                    d:
                    $logBuffer
                    """.trimIndent()
                }
                return ret
            }
    }

    init {
        //empty constructor needed for Rhino
        errorBuffer = StringBuffer()
        logBuffer = StringBuffer()
        @Suppress("DEPRECATION")
        StaticInjector.getInstance().androidInjector().inject(this)
    }
}