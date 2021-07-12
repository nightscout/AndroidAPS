package info.nightscout.androidaps.plugins.pump.medtronic.util

import info.nightscout.androidaps.plugins.pump.medtronic.R

/**
 * Created by andy on 5/12/18.
 */
object MedtronicConst {

    const val Prefix = "AAPS.Medtronic."

    object Prefs {
        @JvmField val PumpSerial = R.string.key_medtronic_serial
        @JvmField val PumpType = R.string.key_medtronic_pump_type
        @JvmField val PumpFrequency = R.string.key_medtronic_frequency
        @JvmField val MaxBolus = R.string.key_medtronic_max_bolus
        @JvmField val MaxBasal = R.string.key_medtronic_max_basal
        @JvmField val BolusDelay = R.string.key_medtronic_bolus_delay
        @JvmField val Encoding = R.string.key_medtronic_encoding
        @JvmField val BatteryType = R.string.key_medtronic_battery_type
        val BolusDebugEnabled = R.string.key_medtronic_bolus_debug
    }

    object Statistics {
        const val StatsPrefix = "medtronic_"
        const val FirstPumpStart = Prefix + "first_pump_use"
        const val LastGoodPumpCommunicationTime = Prefix + "lastGoodPumpCommunicationTime"
        const val LastGoodPumpFrequency = Prefix + "LastGoodPumpFrequency"
        const val TBRsSet = StatsPrefix + "tbrs_set"
        const val StandardBoluses = StatsPrefix + "std_boluses_delivered"
        const val SMBBoluses = StatsPrefix + "smb_boluses_delivered"
        const val LastPumpHistoryEntry = StatsPrefix + "pump_history_entry"
        const val LastPrime = StatsPrefix + "last_sent_prime"
        const val LastRewind = StatsPrefix + "last_sent_rewind"
        const val InternalTemporaryDatabase = StatsPrefix + "temporary_entries"
    }
}