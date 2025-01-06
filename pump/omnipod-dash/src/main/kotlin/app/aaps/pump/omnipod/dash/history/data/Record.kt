package app.aaps.pump.omnipod.dash.history.data

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.profile.Profile

sealed class Record

data class BolusRecord(val amout: Double, val bolusType: BolusType) : Record()

data class TempBasalRecord(val duration: Int, val rate: Double) : Record()

data class BasalValuesRecord(val segments: List<Profile.ProfileValue>) : Record()

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
