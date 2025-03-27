package app.aaps.pump.common.hw.rileylink.ble.defs

import androidx.annotation.StringRes
import app.aaps.pump.common.hw.rileylink.R

/**
 * Created by andy on 6/7/18.
 */
enum class RileyLinkTargetFrequency(val key: String?, @StringRes val friendlyName: Int?, vararg var scanFrequencies: Double) {

    NotSet(null, null),
    MedtronicWorldWide("medtronic_pump_frequency_worldwide", R.string.medtronic_pump_frequency_worldwide, 868.25, 868.3, 868.35, 868.4, 868.45, 868.5, 868.55, 868.6, 868.65),
    MedtronicUS("medtronic_pump_frequency_us_ca", R.string.medtronic_pump_frequency_us_ca, 916.45, 916.5, 916.55, 916.6, 916.65, 916.7, 916.75, 916.8),
    Omnipod(null, null, 433.91);

    companion object {

        fun getByKey(someKey: String): RileyLinkTargetFrequency =
            RileyLinkTargetFrequency.entries.firstOrNull { it.key == someKey } ?: NotSet
    }
}