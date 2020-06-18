package info.nightscout.androidaps.logging

import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class L @Inject constructor(
    private val sp: SP
) {

    private var logElements: MutableList<LogElement> = ArrayList()

    init {
        LTag.values().forEach { logElements.add(LogElement(it, sp)) }
    }

    fun findByName(name: String): LogElement {
        for (element in logElements) {
            if (element.name == name) return element
        }
        return LogElement(false, sp)
    }

    fun getLogElements(): List<LogElement> {
        return logElements
    }

    fun resetToDefaults() {
        for (element in logElements) {
            element.resetToDefault()
        }
    }

    class LogElement {
        var sp: SP
        var name: String
        var defaultValue: Boolean
        var enabled: Boolean
        private var requiresRestart = false

        internal constructor(tag: LTag, sp: SP) {
            this.sp = sp
            this.name = tag.tag
            this.defaultValue = tag.defaultValue
            this.requiresRestart = tag.requiresRestart
            enabled = sp.getBoolean(getSPName(), defaultValue)
        }

        internal constructor(defaultValue: Boolean, sp: SP) {
            this.sp = sp
            name = "NONEXISTING"
            this.defaultValue = defaultValue
            enabled = defaultValue
        }

        private fun getSPName(): String = "log_$name"

        fun enable(enabled: Boolean) {
            this.enabled = enabled
            sp.putBoolean(getSPName(), enabled)
        }

        fun resetToDefault() {
            enable(defaultValue)
        }
    }
}