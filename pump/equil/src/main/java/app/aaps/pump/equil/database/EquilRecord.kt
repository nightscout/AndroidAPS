package app.aaps.pump.equil.database

import app.aaps.core.interfaces.profile.Profile

sealed class EquilRecord

data class EquilBolusRecord(val amount: Double, val bolusType: BolusType, var startTime: Long) : EquilRecord()

data class EquilTempBasalRecord(val duration: Int, val rate: Double, var startTime: Long) : EquilRecord()

data class EquilBasalValuesRecord(val segments: List<Profile.ProfileValue>) : EquilRecord()
enum class ResolvedResult { NONE, SUCCESS, FAILURE, CONNECT_ERROR, NOT_FOUNT }
enum class BolusType { DEFAULT, SMB; }
