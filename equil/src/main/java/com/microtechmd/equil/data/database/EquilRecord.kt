package com.microtechmd.equil.data.database

import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.interfaces.Profile

sealed class EquilRecord

data class EquilBolusRecord(val amout: Double, val bolusType: BolusType, var startTime: Long) : EquilRecord()

data class EquilTempBasalRecord(val duration: Int, val rate: Double, var startTime: Long) : EquilRecord()

data class EquilBasalValuesRecord(val segments: List<Profile.ProfileValue>) : EquilRecord()

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
