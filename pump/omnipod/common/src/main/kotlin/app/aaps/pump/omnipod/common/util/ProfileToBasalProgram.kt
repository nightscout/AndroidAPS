package app.aaps.pump.omnipod.common.util

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import kotlin.math.roundToInt

fun mapProfileToBasalProgram(profile: Profile, pumpType: PumpType): BasalProgram {
    val basalValues = profile.getBasalValues()
    require(basalValues.isNotEmpty()) { "Basal values should contain values" }

    val entries = mutableListOf<BasalProgram.Segment>()
    var previousBasalValue: Profile.ProfileValue? = null

    for (basalValue in basalValues) {
        require(basalValue.timeAsSeconds < 86_400) { "Basal segment start time can not be greater than 86400" }
        require(basalValue.timeAsSeconds >= 0) { "Basal segment start time can not be less than 0" }
        require(basalValue.timeAsSeconds % 1_800 == 0) { "Basal segment time should be dividable by 30 minutes" }

        val startSlotIndex = (basalValue.timeAsSeconds / 1_800).toShort()
        previousBasalValue?.let { previous ->
            entries.add(
                BasalProgram.Segment(
                    (previous.timeAsSeconds / 1_800).toShort(),
                    startSlotIndex,
                    (pumpType.determineCorrectBasalSize(previous.value) * 100).roundToInt()
                )
            )
        }

        require(entries.isNotEmpty() || basalValue.timeAsSeconds == 0) { "First basal segment start time should be 0" }
        require(entries.isEmpty() || entries.last().endSlotIndex == startSlotIndex) {
            "Illegal start time for basal segment: does not match previous previous segment's end time"
        }
        previousBasalValue = basalValue
    }

    val lastBasalValue = requireNotNull(previousBasalValue)
    entries.add(
        BasalProgram.Segment(
            (lastBasalValue.timeAsSeconds / 1_800).toShort(),
            48,
            (pumpType.determineCorrectBasalSize(lastBasalValue.value) * 100).roundToInt()
        )
    )
    return BasalProgram(entries)
}
