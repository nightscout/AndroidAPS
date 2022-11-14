package info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data

import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.DetailedBolusInfo

sealed class Record

data class BolusRecord(val amout: Double, val bolusType: BolusType) : Record()

data class TempBasalRecord(val duration: Int, val rate: Double) : Record()

data class BasalValuesRecord(val segments: List<Profile.ProfileValue>) : Record()

enum class BolusType {
    DEFAULT, SMB;

    fun toBolusInfoBolusType(): DetailedBolusInfo.BolusType {
        return when (this) {
            DEFAULT -> DetailedBolusInfo.BolusType.NORMAL
            SMB -> DetailedBolusInfo.BolusType.SMB
        }
    }

    companion object {
        fun fromBolusInfoBolusType(type: DetailedBolusInfo.BolusType): BolusType {
            return when (type) {
                DetailedBolusInfo.BolusType.SMB -> SMB
                else                            -> DEFAULT
            }
        }
    }
}
