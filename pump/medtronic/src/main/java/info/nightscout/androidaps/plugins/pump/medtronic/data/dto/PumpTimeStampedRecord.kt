package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import info.nightscout.pump.common.utils.StringUtil

/**
 * Created by andy on 6/2/18.
 */
open class PumpTimeStampedRecord constructor(var atechDateTime: Long = 0) {

    var decimalPrecission = 2
    // var atechDateTime: Long = 0

    open fun getFormattedDecimal(value: Double): String? {
        return StringUtil.getFormattedValueUS(value, decimalPrecission)
    }
}