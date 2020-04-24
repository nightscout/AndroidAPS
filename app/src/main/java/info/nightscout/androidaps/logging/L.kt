package info.nightscout.androidaps.logging

import androidx.preference.PreferenceManager
import info.nightscout.androidaps.MainApp
import java.util.*

object L {
    private var logElements: MutableList<LogElement> = ArrayList()

    const val CORE = "CORE"
    const val BGSOURCE = "BGSOURCE"
    const val DATASERVICE = "DATASERVICE"
    const val DATABASE = "DATABASE"
    const val DATAFOOD = "DATAFOOD"
    const val DATATREATMENTS = "DATATREATMENTS"
    const val NSCLIENT = "NSCLIENT"
    const val PUMP = "PUMP"
    const val PUMPCOMM = "PUMPCOMM"
    const val PUMPBTCOMM = "PUMPBTCOMM"

    init {
        LTag.values().forEach { logElements.add(LogElement(it)) }
    }

    private fun findByName(name: String): LogElement {
        for (element in logElements) {
            if (element.name == name) return element
        }
        return LogElement(false)
    }

    @JvmStatic
    fun isEnabled(name: String): Boolean {
        return findByName(name).enabled
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
        var name: String
        var defaultValue: Boolean
        var enabled: Boolean
        private var requiresRestart = false

        internal constructor(tag: LTag) {
            this.name = tag.tag
            this.defaultValue = tag.defaultValue
            this.requiresRestart = tag.requiresRestart
            //TODO: remove after getting rid of old logging style "if (L.isEnabled(...))"
            @Suppress("DEPRECATION")
            enabled = PreferenceManager.getDefaultSharedPreferences(MainApp.instance()).getBoolean(getSPName(), defaultValue)
        }

        internal constructor(defaultValue: Boolean) {
            name = "NONEXISTING"
            this.defaultValue = defaultValue
            enabled = defaultValue
        }

        private fun getSPName(): String = "log_$name"

        fun enable(enabled: Boolean) {
            this.enabled = enabled
            @Suppress("DEPRECATION")
            PreferenceManager.getDefaultSharedPreferences(MainApp.instance()).edit().putBoolean(getSPName(), enabled).apply()
        }

        fun resetToDefault() {
            enable(defaultValue)
        }
    }
}