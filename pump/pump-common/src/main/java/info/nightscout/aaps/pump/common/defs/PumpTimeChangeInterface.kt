package info.nightscout.androidaps.plugins.pump.common.defs

import info.nightscout.aaps.pump.common.data.DateTimeDto

interface PumpTimeChangeInterface {

    fun getTime(): DateTimeDto

    fun getUpdateAction(): PumpTimeUpdateAction






}


enum class PumpTimeUpdateAction {
    NO_ACTION,
    UPDATE_TIME,
    UPDATE_TIME_SHOW_WARNING,
    SHOW_WARNING
}