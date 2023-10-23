package app.aaps.pump.equil.data.database

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo

sealed class EquilRecord

data class EquilBolusRecord(val amout: Double, val bolusType: BolusType, var startTime: Long) : EquilRecord()

data class EquilTempBasalRecord(val duration: Int, val rate: Double, var startTime: Long) : EquilRecord()

data class EquilBasalValuesRecord(val segments: List<Profile.ProfileValue>) : EquilRecord()
enum class ResolvedResult {
    NONE, SUCCESS, FAILURE, CONNECT_ERROR, NOT_FOUNT

}

enum class BolusType {
    DEFAULT, SMB;

    fun toBolusInfoBolusType(): DetailedBolusInfo.BolusType {
        return when (this) {
            DEFAULT -> DetailedBolusInfo.BolusType.NORMAL
            SMB     -> DetailedBolusInfo.BolusType.SMB
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
