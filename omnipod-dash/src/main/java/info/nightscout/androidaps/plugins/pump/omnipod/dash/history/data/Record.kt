package info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data

import info.nightscout.androidaps.data.DetailedBolusInfo

sealed class Record

data class BolusRecord(val amout: Double, val bolusType: BolusType) : Record()

data class TempBasalRecord(val duration: Int, val rate: Double) : Record()

enum class BolusType {
    DEFAULT, SMB;

    companion object {
        fun fromBolusInfoBolusType(type: DetailedBolusInfo.BolusType): BolusType {
            return when (type) {
                DetailedBolusInfo.BolusType.SMB -> SMB;
                else -> DEFAULT
            }
        }
    }
}
