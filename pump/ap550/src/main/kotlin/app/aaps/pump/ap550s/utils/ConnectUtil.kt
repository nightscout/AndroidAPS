package app.aaps.pump.ap550s.utils


import androidx.annotation.StringRes

import app.aaps.pump.ap550s.R
import app.aaps.pump.ap550s.database.model.ConnectHistoryItem

/**
 * Holds the connection history the plugin and the execution service both read. Registered as one
 * instance for the whole app in `AP550Bindings`, because the state is shared between them.
 */
class ConnectUtil {

    var historyLink: List<ConnectHistoryItem> = ArrayList()

    /**
     * Returns the string resource for a pump error code. Callers hold a `ResourceHelper` (or can
     * hand the id to `PumpEnactResult.comment(Int)`), so this class needs no Context of its own.
     */
    @StringRes
    fun errorCode2StringRes(errorCode: Int, treatmentType: Int): Int = when (errorCode) {
        1 -> R.string.ap550_pump_error_01
        2 -> R.string.ap550_pump_error_02
        3 -> R.string.ap550_pump_error_03
        4 -> R.string.ap550_pump_error_04
        5 -> R.string.ap550_pump_error_05
        6 -> R.string.ap550_pump_error_06
        7 -> R.string.ap550_pump_error_07
        8 -> R.string.ap550_pump_error_08
        9 -> R.string.ap550_pump_error_09
        10 -> if (treatmentType == 1) {
            R.string.ap550_pump_error_10_bolus
        } else if (treatmentType == 3) {
            R.string.ap550_pump_error_10_extended
        } else {
            R.string.ap550_pump_error_10_tempbasal
        }
        12 -> if (treatmentType == 1) {
            R.string.ap550_pump_error_12_notenabled
        } else {
            R.string.ap550_pump_error_12_comm
        }
        else -> R.string.ap550_pump_error_00
    }
}