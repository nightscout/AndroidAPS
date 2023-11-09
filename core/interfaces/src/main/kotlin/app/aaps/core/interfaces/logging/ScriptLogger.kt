package app.aaps.core.interfaces.logging

import javax.inject.Singleton

@Singleton
interface ScriptLogger {
    fun debug(message: String)
    fun debugUnits(message: String, value : Double)
    fun debug(message: String, vararg values : Any)
    fun error(message: String)
    fun header(message: String)
    fun footer()

    fun dump() : String {
        return Companion.dump()
    }

    companion object {
        private var errorBuffer = StringBuffer()
        private var logBuffer = StringBuffer()


        fun error(message : String) {
            errorBuffer.append(message + '\n')
        }

        fun debug(message : String) {
            logBuffer.append(message + '\n')
        }

        fun dump() : String {
            var ret = ""
            if (errorBuffer.isNotEmpty()) {
                ret += """
error:
$errorBuffer
""".trimIndent()
                errorBuffer.delete(0, errorBuffer.length)
            }
            if (ret.isNotEmpty() && logBuffer.isNotEmpty()) ret += '\n'
            if (logBuffer.isNotEmpty()) {
                ret += """
debug:
$logBuffer
""".trimIndent()
                logBuffer.delete(0, logBuffer.length)
            }
            return ret
        }
    }
}