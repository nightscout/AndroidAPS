package info.nightscout.androidaps.logging

import info.nightscout.androidaps.utils.SP
import java.util.*

object L {
    private var logElements: MutableList<LogElement> = ArrayList()

    const val CORE = "CORE"
    const val AUTOSENS = "AUTOSENS"
    const val AUTOMATION = "AUTOMATION"
    const val EVENTS = "EVENTS"
    const val GLUCOSE = "GLUCOSE"
    const val BGSOURCE = "BGSOURCE"
    const val OVERVIEW = "OVERVIEW"
    const val NOTIFICATION = "NOTIFICATION"
    const val DATASERVICE = "DATASERVICE"
    const val DATABASE = "DATABASE"
    const val DATAFOOD = "DATAFOOD"
    const val DATATREATMENTS = "DATATREATMENTS"
    const val NSCLIENT = "NSCLIENT"
    const val TIDEPOOL = "TIDEPOOL"
    const val CONSTRAINTS = "CONSTRAINTS"
    const val PUMP = "PUMP"
    const val PUMPQUEUE = "PUMPQUEUE"
    const val PUMPCOMM = "PUMPCOMM"
    const val PUMPBTCOMM = "PUMPBTCOMM"
    const val APS = "APS"
    const val PROFILE = "PROFILE"
    const val CONFIGBUILDER = "CONFIGBUILDER"
    const val UI = "UI"
    const val LOCATION = "LOCATION"
    const val SMS = "SMS"

    init {
        logElements.add(LogElement(APS, defaultValue = true))
        logElements.add(LogElement(AUTOMATION, defaultValue = true))
        logElements.add(LogElement(AUTOSENS, defaultValue = false))
        logElements.add(LogElement(BGSOURCE, defaultValue = true))
        logElements.add(LogElement(GLUCOSE, defaultValue = false))
        logElements.add(LogElement(CONFIGBUILDER, defaultValue = false))
        logElements.add(LogElement(CONSTRAINTS, defaultValue = true))
        logElements.add(LogElement(CORE, defaultValue = true))
        logElements.add(LogElement(DATABASE, defaultValue = true))
        logElements.add(LogElement(DATAFOOD, false))
        logElements.add(LogElement(DATASERVICE, true))
        logElements.add(LogElement(DATATREATMENTS, true))
        logElements.add(LogElement(EVENTS, false, requiresRestart = true))
        logElements.add(LogElement(LOCATION, true))
        logElements.add(LogElement(NOTIFICATION, true))
        logElements.add(LogElement(NSCLIENT, true))
        logElements.add(LogElement(TIDEPOOL, true))
        logElements.add(LogElement(OVERVIEW, true))
        logElements.add(LogElement(PROFILE, true))
        logElements.add(LogElement(PUMP, true))
        logElements.add(LogElement(PUMPBTCOMM, false))
        logElements.add(LogElement(PUMPCOMM, true))
        logElements.add(LogElement(PUMPQUEUE, true))
        logElements.add(LogElement(SMS, true))
        logElements.add(LogElement(UI, true))
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

        internal constructor(name: String, defaultValue: Boolean) {
            this.name = name
            this.defaultValue = defaultValue
            enabled = SP.getBoolean(getSPName(), defaultValue)
        }

        internal constructor(name: String, defaultValue: Boolean, requiresRestart: Boolean) {
            this.name = name
            this.defaultValue = defaultValue
            this.requiresRestart = requiresRestart
            enabled = SP.getBoolean(getSPName(), defaultValue)
        }

        internal constructor(defaultValue: Boolean) {
            name = "NONEXISTING"
            this.defaultValue = defaultValue
            enabled = defaultValue
        }

        private fun getSPName(): String = "log_$name"

        fun enable(enabled: Boolean) {
            this.enabled = enabled
            SP.putBoolean(getSPName(), enabled)
        }

        fun resetToDefault() {
            enable(defaultValue)
        }
    }
}