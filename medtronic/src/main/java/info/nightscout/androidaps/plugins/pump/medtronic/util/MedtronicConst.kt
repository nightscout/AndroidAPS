package info.nightscout.androidaps.plugins.pump.medtronic.util

import info.nightscout.androidaps.plugins.pump.medtronic.R

/**
 * Created by andy on 5/12/18.
 */
object MedtronicConst {

    const val Prefix = "AAPS.Medtronic."

    object Prefs {
        val PumpSerial = R.string.key_medtronic_serial
        val PumpType = R.string.key_medtronic_pump_type
        val PumpFrequency = R.string.key_medtronic_frequency
        val MaxBolus = R.string.key_medtronic_max_bolus
        val MaxBasal = R.string.key_medtronic_max_basal
        val BolusDelay = R.string.key_medtronic_bolus_delay
        val Encoding = R.string.key_medtronic_encoding
        val BatteryType = R.string.key_medtronic_battery_type
    }

    object Statistics {
        private const val StatsPrefix = "medtronic_"
        const val FirstPumpStart = Prefix + "first_pump_use"
        const val LastGoodPumpCommunicationTime = Prefix + "lastGoodPumpCommunicationTime"
        const val TBRsSet = StatsPrefix + "tbrs_set"
        const val StandardBoluses = StatsPrefix + "std_boluses_delivered"
        const val SMBBoluses = StatsPrefix + "smb_boluses_delivered"
        const val LastPumpHistoryEntry = StatsPrefix + "pump_history_entry"
        const val LastPrime = StatsPrefix + "last_sent_prime"
        const val LastRewind = StatsPrefix + "last_sent_rewind"
    }
}