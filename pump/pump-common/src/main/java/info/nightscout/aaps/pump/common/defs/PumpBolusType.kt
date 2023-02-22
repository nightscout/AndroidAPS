package info.nightscout.aaps.pump.common.defs

import androidx.annotation.StringRes
import info.nightscout.aaps.pump.common.R

enum class PumpBolusType(@StringRes var resourceId: Int) {
    NORMAL(R.string.pump_bolus_normal),
    EXTENDED(R.string.pump_bolus_extended),
    COMBINED(R.string.pump_bolus_combined),
    SMB(R.string.pump_bolus_smb),
    PRIME(R.string.pump_bolus_prime)
}