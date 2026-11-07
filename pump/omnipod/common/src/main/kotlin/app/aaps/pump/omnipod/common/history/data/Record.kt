package app.aaps.pump.omnipod.common.history.data

import app.aaps.core.interfaces.profile.Profile
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BolusType

sealed class Record

data class BolusRecord(val amount: Double, val bolusType: BolusType) : Record()

data class TempBasalRecord(val duration: Int, val rate: Double) : Record()

data class BasalValuesRecord(val segments: List<Profile.ProfileValue>) : Record()
