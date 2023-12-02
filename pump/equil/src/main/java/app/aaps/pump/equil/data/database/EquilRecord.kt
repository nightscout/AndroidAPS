package app.aaps.pump.equil.data.database

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.profile.Profile

sealed class EquilRecord

data class EquilBolusRecord(val amout: Double, val bolusType: BolusType, var startTime: Long) : EquilRecord()

data class EquilTempBasalRecord(val duration: Int, val rate: Double, var startTime: Long) : EquilRecord()

data class EquilBasalValuesRecord(val segments: List<Profile.ProfileValue>) : EquilRecord()
enum class ResolvedResult {
    NONE, SUCCESS, FAILURE, CONNECT_ERROR, NOT_FOUNT

}

enum class BolusType {
    DEFAULT, SMB;

    fun toBolusInfoBolusType(): BS.Type {
        return when (this) {
            DEFAULT -> BS.Type.NORMAL
            SMB     -> BS.Type.SMB
        }
    }

    companion object {

        fun fromBolusInfoBolusType(type: BS.Type): BolusType {
            return when (type) {
                BS.Type.SMB -> SMB
                else        -> DEFAULT
            }
        }
    }
}
